import { Component, computed, input, output } from '@angular/core';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MathNode, nodeAtPath, substituteVariable } from 'app/math/shared/entities/math-node.model';
import { InductionDatatype, MathProblem } from 'app/math/shared/entities/math-problem.model';
import { IH_RULE_ID, InductionHypothesis, InductionSchema, hypothesesFor, ihRulesFor, schemaFor, termToPlain } from 'app/math/shared/entities/induction-schema';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';
import { BlockDefinitionModel, RewriteRuleModel } from 'app/math/shared/entities/block-definition.model';
import { MathNodeLatexPipe } from 'app/math/shared/math-node-latex.pipe';
import { MathProblemParticipationComponent } from 'app/math/participate/math-problem-participation/math-problem-participation.component';

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

    /** The variable inducted over (defaults to {@code n}). */
    readonly inductionVar = computed<string>(() => this.problem().inductionVariable || 'n');

    /** The datatype inducted over (defaults to ℕ). Drives the base/step constructors and the induction hypotheses. */
    readonly inductionDatatype = computed<InductionDatatype>(() => this.problem().inductionDatatype ?? 'NAT');

    /** The base/step constructors and recursive fields for the current datatype (ℕ: {@code 0}/{@code S n}, one IH). */
    readonly inductionSchema = computed<InductionSchema>(() => schemaFor(this.inductionDatatype(), this.inductionVar()));

    /** Human-readable base constructor for the section labels (e.g. {@code 0}, {@code nil}, {@code empty}). */
    readonly baseTermLabel = computed<string>(() => termToPlain(this.inductionSchema().baseTerm));

    /** Human-readable step constructor for the section labels (e.g. {@code S n}, {@code cons h t}, {@code node l v r}). */
    readonly stepTermLabel = computed<string>(() => termToPlain(this.inductionSchema().stepTerm));

    /**
     * The induction hypotheses — one per recursive field of the step constructor: {@code P(n)} for ℕ, {@code P(t)}
     * for a list, {@code P(l)} and {@code P(r)} for a binary tree. Each is the goal with the induction variable
     * replaced by that recursive field; the field variable is what stays literal when the IH is schematised.
     */
    readonly hypotheses = computed<InductionHypothesis[]>(() => hypothesesFor(this.problem()));

    /** The primary hypothesis, used as a fallback substitution equation when the concrete instance cannot be read. */
    readonly hypothesis = computed<MathNode | undefined>(() => this.hypotheses()[0]?.equation);

    /** Synthetic base-case problem: prove {@code P(base)} (e.g. {@code P(0)} / {@code P(nil)}) as an equation. */
    readonly baseProblem = computed<MathProblem>(() => this.syntheticProblem(this.substitutedGoal(this.inductionSchema().baseTerm)));

    /** Synthetic inductive-step problem: prove {@code P(step)} (e.g. {@code P(S n)} / {@code P(cons h t)}) as an equation. */
    readonly stepProblem = computed<MathProblem>(() => this.syntheticProblem(this.substitutedGoal(this.inductionSchema().stepTerm)));

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

    /** The step workspace's rule palette: the base palette (catalogue + definitions) plus each induction hypothesis as an applicable (Leibniz) rule. */
    readonly stepBlocks = computed<BlockDefinitionModel[]>(() => {
        const base = this.baseBlocks();
        // Built by the shared helper so the instructor's example-solution editor offers the identical palette.
        const ihRules: RewriteRuleModel[] = ihRulesFor(this.problem());
        if (!ihRules.length) {
            return base;
        }
        const ihBlock: BlockDefinitionModel = { type: 'hypothesis', category: 'induction', label: 'Hypothesis', paletteLatex: '', slots: [], rules: ihRules };
        return [...base, ihBlock];
    });

    readonly initialBaseSteps = computed<DerivationStep[]>(() => this.initialSteps().filter((s) => s.derivationRole === 'BASE'));
    readonly initialStepSteps = computed<DerivationStep[]>(() => this.initialSteps().filter((s) => s.derivationRole === 'STEP'));

    onBaseSteps(steps: DerivationStep[]): void {
        this.baseSteps = steps.map((s) => ({ ...s, derivationRole: 'BASE' as const, kind: 'A' as const }));
        this.emit();
    }

    onStepSteps(steps: DerivationStep[]): void {
        // A step that applied the hypothesis rule is a Leibniz (kind-B) substitution. Because the IH is now the
        // schema ∀x⃗. P(x⃗, n) (see stepBlocks), the equality it carries is the concrete *instance* the student
        // used — read straight off the trees as (subterm before) = (subterm after) at the rewrite site — not the
        // generic P(n). This is the instance the backend re-checks (Regate capabilities C2/D2). Everything else is
        // a rule application.
        let previous = this.stepProblem().goalExpression;
        this.stepSteps = steps.map((s) => {
            const before = previous;
            previous = s.resultExpression;
            // Any hypothesis rule (`induction_hypothesis`, or `induction_hypothesis_l`/`_r` for a tree's two IHs).
            if (s.appliedRuleId?.startsWith(IH_RULE_ID)) {
                const equation = this.instantiatedHypothesis(before, s.resultExpression, s.targetNodePath ?? []);
                return { ...s, derivationRole: 'STEP' as const, kind: 'B' as const, substitutionEquation: equation ?? this.hypothesis() };
            }
            return { ...s, derivationRole: 'STEP' as const, kind: 'A' as const };
        });
        this.emit();
    }

    /**
     * The concrete IH instance a kind-B step applied: `(subterm at `path` before the step) = (subterm after)`.
     * For a forward IH application this is exactly `(P.lhs·σ, P.rhs·σ)` — the equality the backend re-checks as a
     * genuine instance of the schematic hypothesis. Returns {@code undefined} if either subterm cannot be located.
     */
    private instantiatedHypothesis(before: MathNode | undefined, after: MathNode | undefined, path: number[]): MathNode | undefined {
        if (!before || !after) {
            return undefined;
        }
        try {
            const left = nodeAtPath(before, path);
            const right = nodeAtPath(after, path);
            if (!left || !right) {
                return undefined;
            }
            return { type: 'eq', slots: { left: [left], right: [right] } };
        } catch {
            return undefined;
        }
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
