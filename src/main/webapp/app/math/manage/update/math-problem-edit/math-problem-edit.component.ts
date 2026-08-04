import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MathBlockRegistryService } from '../../service/math-block-registry.service';
import { MathExerciseService } from '../../service/math-exercise.service';
import { INDUCTION_DATATYPE_LABELS, InductionDatatype, MathProblem } from '../../../shared/entities/math-problem.model';
import { MathNode } from '../../../shared/entities/math-node.model';
import { DerivationStep } from '../../../shared/entities/derivation-step.model';
import { GRADER_TYPE_LABELS, GraderType, defaultGradersForMode, graderSupportsMode } from '../../../shared/entities/grader-type.model';
import { GOAL_MODE_LABELS, GoalMode } from '../../../shared/entities/goal-mode.model';
import { ReachabilityReport } from '../../../shared/entities/hint-suggestion.model';
import { MathBuilderComponent } from '../math-builder/math-builder.component';
import { MathInductionExampleWorkspaceComponent } from 'app/math/manage/update/math-induction-example-workspace/math-induction-example-workspace.component';
import { MathDerivationWorkspaceComponent } from '../math-derivation-workspace/math-derivation-workspace.component';
import { MATH_STARTER_TEMPLATES } from '../math-starter-templates';

/**
 * Instructor edit UI for a single {@link MathProblem}. The problem is mutated in place — the parent holds the same
 * object reference, so template two-way bindings via {@code [(ngModel)]="problem().field"} write straight through.
 */
@Component({
    selector: 'jhi-math-problem-edit',
    templateUrl: './math-problem-edit.component.html',
    styleUrl: './math-problem-edit.component.scss',
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        MathBuilderComponent,
        MathDerivationWorkspaceComponent,
        MathInductionExampleWorkspaceComponent,
        ButtonModule,
        CardModule,
        CheckboxModule,
        InputTextModule,
        MessageModule,
        MultiSelectModule,
        SelectModule,
        TagModule,
        TooltipModule,
    ],
})
export class MathProblemEditComponent implements OnInit {
    private mathExerciseService = inject(MathExerciseService);
    private blockRegistryService = inject(MathBlockRegistryService);
    private translateService = inject(TranslateService);

    readonly problem = input.required<MathProblem>();
    readonly exerciseId = input<number | undefined>();
    readonly problemChange = output<MathProblem>();

    /** Editor-only preview toggle for the example-derivation workspace (not persisted on the problem). */
    readonly previewOnlyApplicableRules = signal(false);

    readonly reachability = signal<ReachabilityReport | undefined>(undefined);
    readonly reachabilityChecking = signal(false);
    readonly reachabilityError = signal<string | undefined>(undefined);

    /**
     * Warms the shared block-registry cache that {@link ruleSubsetOptionGroups} reads. The service caches, so this is a
     * no-op beyond the first component that asks for it.
     */
    ngOnInit(): void {
        this.blockRegistryService.getBlockRegistry().subscribe();
    }

    /** Grader options for a given goal mode; graders that can't grade the mode are disabled (mirrors the server matrix). */
    graderOptionsFor(mode: GoalMode): { value: GraderType; label: string; disabled: boolean }[] {
        return (Object.keys(GRADER_TYPE_LABELS) as GraderType[]).map((value) => ({
            value,
            label: GRADER_TYPE_LABELS[value],
            disabled: !graderSupportsMode(value, mode),
        }));
    }

    /**
     * Whether the path checker is listed after a stronger (remote) backend, i.e. acts as a fallback. In that position it
     * only grades when the stronger backend is unavailable, and only its full-pass verdicts are trusted — everything else
     * routes to review. Surfaced as an authoring hint so instructors understand the interaction (mirrors the server's
     * strength-based arbitration). All non-PATH_CHECKER graders are stronger, so "not first" is sufficient.
     */
    pathCheckerActsAsFallback(): boolean {
        const graders = this.problem().graderTypes ?? [];
        return graders.indexOf('PATH_CHECKER') > 0;
    }

    /** Certifier candidates: the remote formal backends (everything but the in-process PATH_CHECKER) that support this mode. */
    certifierOptionsFor(mode: GoalMode): { value: GraderType; label: string }[] {
        return (Object.keys(GRADER_TYPE_LABELS) as GraderType[])
            .filter((value) => value !== 'PATH_CHECKER' && graderSupportsMode(value, mode))
            .map((value) => ({ value, label: GRADER_TYPE_LABELS[value] }));
    }

    /**
     * Rule options for the allowed-rule multiselect, grouped by registry block.
     * <p>
     * Only {@code block.rules} is offered. Recursive definitions ({@code block.definitions}: {@code pow_succ},
     * {@code fact_zero}, …) are deliberately left out because the server's {@code RuleSubsetPolicy} exempts them — they
     * are never restricted, so listing them would imply a control the instructor does not have.
     */
    readonly ruleSubsetOptionGroups = computed(() =>
        this.blockRegistryService
            .blocks()
            .map((block) => ({
                label: block.label,
                rules: (block.rules ?? []).map((rule) => ({ value: rule.id, label: rule.name })),
            }))
            .filter((group) => group.rules.length > 0),
    );

    /**
     * Whether the problem places no restriction on the citable rules. Both {@code undefined} and {@code []} mean
     * unrestricted server-side, and an empty multiselect reads as "no rules allowed" — so the template says so out loud.
     * A method, not a {@code computed}, because the problem object is mutated in place and signals do not see that.
     */
    ruleSubsetUnrestricted(): boolean {
        return !this.problem().allowedRuleIds?.length;
    }

    /**
     * Writes the selected rule subset onto the problem. An empty selection is stored as {@code undefined} rather than
     * {@code []}: both are unrestricted to the server, but {@code undefined} is the "no subset authored" state the rest
     * of the client already round-trips, and persisting an empty array would suggest a subset was deliberately emptied.
     */
    onAllowedRuleIdsChange(ruleIds: string[] | undefined): void {
        this.problem().allowedRuleIds = ruleIds?.length ? ruleIds : undefined;
        this.problemChange.emit(this.problem());
    }

    readonly goalModeOptions: { value: GoalMode; label: string }[] = (Object.keys(GOAL_MODE_LABELS) as GoalMode[]).map((value) => ({
        value,
        label: GOAL_MODE_LABELS[value],
    }));

    /** Options for the induction-datatype picker (ℕ / list / binary tree), shown only in INDUCTION mode. */
    readonly inductionDatatypeOptions: { value: InductionDatatype; label: string }[] = (Object.keys(INDUCTION_DATATYPE_LABELS) as InductionDatatype[]).map((value) => ({
        value,
        label: INDUCTION_DATATYPE_LABELS[value],
    }));

    /** Transient selection for the starter-template picker (acts as an action menu, not persisted on the problem). */
    selectedTemplateId: string | undefined;

    /** Options for the starter-template picker, with translated names and descriptions. */
    readonly starterTemplateOptions = computed(() =>
        MATH_STARTER_TEMPLATES.map((template) => ({
            value: template.id,
            label: this.translateService.instant(template.nameKey),
            description: this.translateService.instant(template.descriptionKey),
        })),
    );

    /** Applies the selected starter template to the current problem (pre-fills goal mode, expressions, and grader). */
    applyStarterTemplate(templateId: string | undefined): void {
        const template = MATH_STARTER_TEMPLATES.find((candidate) => candidate.id === templateId);
        if (!template) {
            return;
        }
        template.apply(this.problem());
        this.reachability.set(undefined);
        this.problemChange.emit(this.problem());
    }

    onSourceExpressionChange(node: MathNode | undefined): void {
        this.problem().sourceExpression = node;
        // The example derivation is tied to the previous start expression — reset it.
        this.problem().exampleDerivations = [];
        this.problemChange.emit(this.problem());
    }

    onTargetExpressionChange(node: MathNode | undefined): void {
        this.problem().targetExpression = node;
        // The workspace re-evaluates isComplete automatically via the targetExpression signal input.
        this.problemChange.emit(this.problem());
    }

    onGoalExpressionChange(node: MathNode | undefined): void {
        this.problem().goalExpression = node;
        this.problem().exampleDerivations = [];
        this.problemChange.emit(this.problem());
    }

    onGoalModeChange(mode: GoalMode): void {
        this.problem().goalMode = mode;
        // Drop any selected graders that can't grade the new mode; fall back to a compatible default if none remain
        // (avoids a save-time 400).
        const supported = (this.problem().graderTypes ?? []).filter((grader) => graderSupportsMode(grader, mode));
        this.problem().graderTypes = supported.length > 0 ? supported : defaultGradersForMode(mode);
        // Clear the certifier if it no longer supports the new mode.
        const certifier = this.problem().certifyingGraderType;
        if (certifier && !graderSupportsMode(certifier, mode)) {
            this.problem().certifyingGraderType = undefined;
        }
        // Reset the example derivation — it's tied to the previous start expression.
        this.problem().exampleDerivations = [];
        this.reachability.set(undefined);
        this.problemChange.emit(this.problem());
    }

    onInductionDatatypeChange(datatype: InductionDatatype): void {
        this.problem().inductionDatatype = datatype;
        // The base/step constructors (0/S n vs nil/cons vs empty/node) change with the datatype, so any recorded
        // example derivation no longer matches — reset it.
        this.problem().exampleDerivations = [];
        this.reachability.set(undefined);
        this.problemChange.emit(this.problem());
    }

    onExampleStepsChange(steps: DerivationStep[]): void {
        this.problem().exampleDerivations = steps;
        this.problemChange.emit(this.problem());
    }

    clearExampleDerivation(): void {
        this.problem().exampleDerivations = [];
        this.problemChange.emit(this.problem());
    }

    checkReachability(): void {
        const exerciseId = this.exerciseId();
        const problemId = this.problem().id;
        if (exerciseId === undefined || problemId === undefined) {
            this.reachabilityError.set('artemisApp.mathExercise.reachability.saveFirst');
            return;
        }
        this.reachabilityError.set(undefined);
        this.reachabilityChecking.set(true);
        this.mathExerciseService.verifyReachability(exerciseId, problemId).subscribe({
            next: (report) => {
                this.reachability.set(report);
                if (!report) {
                    this.reachabilityError.set('artemisApp.mathExercise.reachability.notSupported');
                }
                this.reachabilityChecking.set(false);
            },
            error: () => {
                this.reachability.set(undefined);
                this.reachabilityError.set('artemisApp.mathExercise.reachability.failed');
                this.reachabilityChecking.set(false);
            },
        });
    }
}
