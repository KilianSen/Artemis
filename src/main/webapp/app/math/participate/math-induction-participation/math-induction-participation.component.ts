import { Component, computed, input, output } from '@angular/core';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MathNode, mathNodeToLatex, substituteVariable } from 'app/math/shared/entities/math-node.model';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';
import { BlockDefinitionModel, RewriteRuleModel } from 'app/math/shared/entities/block-definition.model';
import { MathNodeLatexPipe } from 'app/math/shared/math-node-latex.pipe';
import { MathProblemParticipationComponent } from 'app/math/participate/math-problem-participation/math-problem-participation.component';

/** The synthetic rule id under which the induction hypothesis P(n) is offered as an applicable rule in the step workspace. */
const IH_RULE_ID = 'induction_hypothesis';

/**
 * Participation editor for an INDUCTION problem. It reduces the proof to two ordinary equation derivations —
 * the base case {@code P(0)} and the inductive step {@code P(S n)} — each rendered by a reused
 * {@link MathProblemParticipationComponent} over a synthetic EQUATION problem whose goal is the induction goal
 * with the induction variable substituted (by {@code n → 0} and {@code n → S(n)} respectively).
 * <p>
 * Steps from the two workspaces are tagged with their {@code derivationRole} (BASE / STEP) and concatenated
 * into a single ordered list, so the container assembles one answer exactly as for a normal problem and the
 * server splits it back by role. The induction hypothesis {@code P(n)} is offered as an applicable (Leibniz)
 * rule in the step workspace; steps applying it are tagged kind-B and carry the equality for the grader.
 */
@Component({
    selector: 'jhi-math-induction-participation',
    templateUrl: './math-induction-participation.component.html',
    styleUrl: './math-induction-participation.component.scss',
    imports: [MathProblemParticipationComponent, CardModule, MessageModule, TranslateDirective, MathNodeLatexPipe],
})
export class MathInductionParticipationComponent {
    readonly problem = input.required<MathProblem>();
    readonly exerciseId = input.required<number>();
    readonly initialSteps = input<DerivationStep[]>([]);
    readonly submitted = input<boolean>(false);
    readonly problemIndex = input<number>(0);
    readonly blocks = input<BlockDefinitionModel[]>([]);

    readonly stepsChange = output<DerivationStep[]>();

    private baseSteps: DerivationStep[] = [];
    private stepSteps: DerivationStep[] = [];

    /** The ℕ variable inducted over (defaults to {@code n}). */
    readonly inductionVar = computed<string>(() => this.problem().inductionVariable || 'n');

    /** The induction hypothesis shown to the student for the step case: P(n) — the original goal. */
    readonly hypothesis = computed<MathNode | undefined>(() => this.problem().goalExpression);

    /** Synthetic base-case problem: prove P(0) as an equation (reduce to a tautology). */
    readonly baseProblem = computed<MathProblem>(() => this.syntheticProblem(this.substitutedGoal({ type: 'number', value: '0' })));

    /** Synthetic inductive-step problem: prove P(S n) as an equation. */
    readonly stepProblem = computed<MathProblem>(() =>
        this.syntheticProblem(this.substitutedGoal({ type: 'succ', slots: { inner: [{ type: 'variable', value: this.inductionVar() }] } })),
    );

    /**
     * A synthetic palette block holding the code-contributed recursive definitions (e.g. pow_zero / pow_succ),
     * so the student can apply them in both the base and step workspaces — they are what drive the induction.
     */
    readonly definitionsBlock = computed<BlockDefinitionModel | undefined>(() => {
        const defs = this.blocks().flatMap((b) => b.definitions ?? []);
        if (!defs.length) {
            return undefined;
        }
        return { type: 'definitions', category: 'induction', label: 'Definitions', paletteLatex: '', slots: [], rules: defs };
    });

    /** The base workspace's rule palette: the catalogue plus the recursive definitions. */
    readonly baseBlocks = computed<BlockDefinitionModel[]>(() => {
        const db = this.definitionsBlock();
        return db ? [...this.blocks(), db] : this.blocks();
    });

    /** The step workspace's rule palette: the base palette (catalogue + definitions) plus the induction hypothesis P(n) as an applicable (Leibniz) rule. */
    readonly stepBlocks = computed<BlockDefinitionModel[]>(() => {
        const base = this.baseBlocks();
        const ih = this.hypothesis();
        const lhs = ih?.slots?.['left']?.[0];
        const rhs = ih?.slots?.['right']?.[0];
        if (!ih || !lhs || !rhs) {
            return base;
        }
        // Pattern uses the literal induction variable (not a wildcard), so it matches P(n) exactly and never P(S n) — the exact-match soundness the protocol requires.
        const ihRule: RewriteRuleModel = {
            id: IH_RULE_ID,
            name: 'Induction hypothesis',
            paletteLatex: mathNodeToLatex(ih),
            pattern: lhs,
            template: rhs,
            direction: 'BIDIRECTIONAL',
            constraints: [],
        };
        const ihBlock: BlockDefinitionModel = { type: 'hypothesis', category: 'induction', label: 'Hypothesis', paletteLatex: '', slots: [], rules: [ihRule] };
        return [...base, ihBlock];
    });

    readonly initialBaseSteps = computed<DerivationStep[]>(() => this.initialSteps().filter((s) => s.derivationRole === 'BASE'));
    readonly initialStepSteps = computed<DerivationStep[]>(() => this.initialSteps().filter((s) => s.derivationRole === 'STEP'));

    onBaseSteps(steps: DerivationStep[]): void {
        this.baseSteps = steps.map((s) => ({ ...s, derivationRole: 'BASE' as const, kind: 'A' as const }));
        this.emit();
    }

    onStepSteps(steps: DerivationStep[]): void {
        const ih = this.hypothesis();
        // A step that applied the hypothesis rule is a Leibniz (kind-B) substitution carrying the equality P(n); everything else is a rule application.
        this.stepSteps = steps.map((s) =>
            s.appliedRuleId === IH_RULE_ID
                ? { ...s, derivationRole: 'STEP' as const, kind: 'B' as const, substitutionEquation: ih }
                : { ...s, derivationRole: 'STEP' as const, kind: 'A' as const },
        );
        this.emit();
    }

    private emit(): void {
        this.stepsChange.emit([...this.baseSteps, ...this.stepSteps]);
    }

    private substitutedGoal(replacement: MathNode): MathNode | undefined {
        const goal = this.problem().goalExpression;
        return goal ? substituteVariable(goal, this.inductionVar(), replacement) : undefined;
    }

    /** A copy of the problem reframed as an EQUATION over the given goal, inheriting its grading config. */
    private syntheticProblem(goalExpression: MathNode | undefined): MathProblem {
        const synthetic = new MathProblem();
        Object.assign(synthetic, this.problem());
        synthetic.goalMode = 'EQUATION';
        synthetic.goalExpression = goalExpression;
        synthetic.sourceExpression = undefined;
        synthetic.targetExpression = undefined;
        return synthetic;
    }
}
