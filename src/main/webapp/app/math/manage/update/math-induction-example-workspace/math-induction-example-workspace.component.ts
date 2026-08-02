import { Component, computed, input, output } from '@angular/core';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import { MathNode, nodeAtPath } from 'app/math/shared/entities/math-node.model';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';
import { BlockDefinitionModel } from 'app/math/shared/entities/block-definition.model';
import { MathNodeLatexPipe } from 'app/math/shared/math-node-latex.pipe';
import { ihRulesFor, schemaOf, substitutedGoal, termToPlain } from 'app/math/shared/entities/induction-schema';
import { MathDerivationWorkspaceComponent } from 'app/math/manage/update/math-derivation-workspace/math-derivation-workspace.component';

/**
 * Instructor-side editor for an INDUCTION problem's example solution. It mirrors what the student sees in
 * {@code MathInductionParticipationComponent}: the proof reduces to two ordinary equation derivations — the base case
 * {@code P(base)} and the inductive step {@code P(step)} — each rendered by a reused
 * {@link MathDerivationWorkspaceComponent} over the goal with the induction variable substituted.
 * <p>
 * The two workspaces' steps are tagged with their {@code derivationRole} (BASE / STEP) and concatenated into one
 * ordered list, exactly the shape the student editor emits and the grader splits back by role — so a sample solution
 * is stored, replayed and graded identically to a submission. The induction hypotheses are offered in the step
 * workspace's palette as Leibniz rules; steps applying one are tagged kind-B and carry the equality for the grader.
 */
@Component({
    selector: 'jhi-math-induction-example-workspace',
    templateUrl: './math-induction-example-workspace.component.html',
    imports: [MathDerivationWorkspaceComponent, CardModule, MessageModule, TagModule, MathNodeLatexPipe],
})
export class MathInductionExampleWorkspaceComponent {
    readonly problem = input.required<MathProblem>();
    readonly initialSteps = input<DerivationStep[]>([]);
    readonly onlyShowApplicableRules = input<boolean>(false);

    readonly stepsChange = output<DerivationStep[]>();

    // Undefined means "this case has not been edited in this session", in which case the previously authored steps
    // stand. Seeding these from initialSteps up front does not work: signal inputs are not readable in the
    // constructor, so editing one case would silently drop the other.
    private baseSteps: DerivationStep[] | undefined;
    private stepSteps: DerivationStep[] | undefined;

    readonly schema = computed(() => schemaOf(this.problem()));

    /** Human-readable constructors for the section headings, e.g. {@code 0} / {@code S n}, {@code empty} / {@code node l v r}. */
    readonly baseTermLabel = computed<string>(() => termToPlain(this.schema().baseTerm));
    readonly stepTermLabel = computed<string>(() => termToPlain(this.schema().stepTerm));

    /** The base-case obligation {@code P(base)}, proved as an equation (closed by tautology). */
    readonly baseGoal = computed<MathNode | undefined>(() => substitutedGoal(this.problem(), this.schema().baseTerm));

    /** The inductive-step obligation {@code P(step)}, proved as an equation with the IH in scope. */
    readonly stepGoal = computed<MathNode | undefined>(() => substitutedGoal(this.problem(), this.schema().stepTerm));

    /** The induction hypotheses as an extra palette block, offered only in the step workspace. */
    readonly hypothesisBlocks = computed<BlockDefinitionModel[]>(() => {
        const rules = ihRulesFor(this.problem());
        if (!rules.length) {
            return [];
        }
        return [{ type: 'hypothesis', category: 'induction', label: 'Hypothesis', paletteLatex: '', slots: [], rules }];
    });

    readonly initialBaseSteps = computed<DerivationStep[]>(() => this.initialSteps().filter((s) => s.derivationRole === 'BASE'));
    readonly initialStepSteps = computed<DerivationStep[]>(() => this.initialSteps().filter((s) => s.derivationRole === 'STEP'));

    onBaseSteps(steps: DerivationStep[]): void {
        this.baseSteps = steps.map((s) => ({ ...s, derivationRole: 'BASE' as const, kind: 'A' as const }));
        this.emit();
    }

    onStepSteps(steps: DerivationStep[]): void {
        const ihRuleIds = new Set(ihRulesFor(this.problem()).map((r) => r.id));
        this.stepSteps = steps.map((s, index) => {
            // A step citing an induction hypothesis is a Leibniz (kind-B) substitution. The grader re-checks the
            // *concrete instance* the step applies, so record `(subterm before, subterm after)` at the rewrite site —
            // for a forward application exactly `(P.lhs·σ, P.rhs·σ)`. Mirrors MathInductionParticipationComponent.
            if (ihRuleIds.has(s.appliedRuleId)) {
                const before = index === 0 ? this.stepGoal() : steps[index - 1].resultExpression;
                const equation = this.instantiatedHypothesis(before, s.resultExpression, s.targetNodePath ?? []);
                return { ...s, derivationRole: 'STEP' as const, kind: 'B' as const, substitutionEquation: s.substitutionEquation ?? equation };
            }
            return { ...s, derivationRole: 'STEP' as const, kind: 'A' as const };
        });
        this.emit();
    }

    /**
     * The equality the kind-B step actually used: the subterms at the rewrite path before and after the step.
     * Returns undefined when either subterm cannot be located, leaving the equation unset rather than wrong.
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
        // An un-edited case falls back to what was authored previously, so saving after touching only one case
        // preserves the other.
        this.stepsChange.emit([...(this.baseSteps ?? this.initialBaseSteps()), ...(this.stepSteps ?? this.initialStepSteps())]);
    }
}
