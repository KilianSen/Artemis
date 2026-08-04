import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import { MathNode, nodeAtPath } from 'app/math/shared/entities/math-node.model';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';
import { BlockDefinitionModel } from 'app/math/shared/entities/block-definition.model';
import { MathNodeLatexPipe } from 'app/math/shared/math-node-latex.pipe';
import { ihRulesFor, inductionPaletteBlocksFor, schemaOf, substitutedGoal, termToPlain } from 'app/math/shared/entities/induction-schema';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
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
 * <p>
 * The palette of each case is the student's palette for that case, built by the shared {@code inductionPaletteBlocksFor}:
 * the rule catalogue plus the recursive definitions ({@code pow_zero}, {@code summa_nil}, …), and in the step case the
 * hypotheses. The definitions are what drive an induction proof, so without them the instructor could not author the
 * derivation their students are expected to produce.
 */
@Component({
    selector: 'jhi-math-induction-example-workspace',
    templateUrl: './math-induction-example-workspace.component.html',
    imports: [MathDerivationWorkspaceComponent, CardModule, MessageModule, TagModule, MathNodeLatexPipe],
})
export class MathInductionExampleWorkspaceComponent implements OnInit {
    private blockRegistryService = inject(MathBlockRegistryService);

    readonly problem = input.required<MathProblem>();
    readonly initialSteps = input<DerivationStep[]>([]);
    readonly onlyShowApplicableRules = input<boolean>(false);

    readonly stepsChange = output<DerivationStep[]>();

    // Undefined means "this case has not been edited in this session", in which case the previously authored steps
    // stand. Seeding these from initialSteps up front does not work: signal inputs are not readable in the
    // constructor, so editing one case would silently drop the other.
    private baseSteps: DerivationStep[] | undefined;
    private stepSteps: DerivationStep[] | undefined;

    /**
     * The rule catalogue, fetched here rather than taken from the case workspaces: the definitions live in the
     * catalogue blocks' {@code definitions} arrays, and this component — not the generic workspace — is what knows
     * that an induction case offers them.
     */
    private readonly registryBlocks = signal<BlockDefinitionModel[]>([]);

    readonly schema = computed(() => schemaOf(this.problem()));

    /**
     * The problem's rule subset, handed to both case workspaces so the palette cannot offer a rule the instructor
     * switched off. Undefined or empty means unrestricted. The induction hypotheses stay offered regardless — they are
     * synthetic ids that exist in no registry and are exempt from the narrowing (see {@code filterBlocksByRuleSubset}).
     */
    readonly allowedRuleIds = computed<string[] | undefined>(() => this.problem().allowedRuleIds);

    /** Human-readable constructors for the section headings, e.g. {@code 0} / {@code S n}, {@code empty} / {@code node l v r}. */
    readonly baseTermLabel = computed<string>(() => termToPlain(this.schema().baseTerm));
    readonly stepTermLabel = computed<string>(() => termToPlain(this.schema().stepTerm));

    /** The base-case obligation {@code P(base)}, proved as an equation (closed by tautology). */
    readonly baseGoal = computed<MathNode | undefined>(() => substitutedGoal(this.problem(), this.schema().baseTerm));

    /** The inductive-step obligation {@code P(step)}, proved as an equation with the IH in scope. */
    readonly stepGoal = computed<MathNode | undefined>(() => substitutedGoal(this.problem(), this.schema().stepTerm));

    /**
     * What each case workspace adds to the rule catalogue it fetches itself: the recursive definitions in both cases,
     * plus the induction hypotheses in the inductive step. Built by the same helper the student editor uses, so the
     * palette an instructor authors with is the palette their students get for that case — without the definitions the
     * instructor could not author the very derivation the students must produce.
     */
    readonly baseExtraBlocks = computed<BlockDefinitionModel[]>(() => inductionPaletteBlocksFor(this.registryBlocks(), this.problem(), 'BASE'));
    readonly stepExtraBlocks = computed<BlockDefinitionModel[]>(() => inductionPaletteBlocksFor(this.registryBlocks(), this.problem(), 'STEP'));

    readonly initialBaseSteps = computed<DerivationStep[]>(() => this.initialSteps().filter((s) => s.derivationRole === 'BASE'));
    readonly initialStepSteps = computed<DerivationStep[]>(() => this.initialSteps().filter((s) => s.derivationRole === 'STEP'));

    ngOnInit(): void {
        this.blockRegistryService.getBlockRegistry().subscribe({
            next: (blocks) => this.registryBlocks.set(blocks),
        });
    }

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
