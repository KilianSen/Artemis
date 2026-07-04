import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MathExerciseService } from '../../service/math-exercise.service';
import { MathProblem } from '../../../shared/entities/math-problem.model';
import { MathNode } from '../../../shared/entities/math-node.model';
import { DerivationStep } from '../../../shared/entities/derivation-step.model';
import { GRADER_TYPES_AVAILABLE, GRADER_TYPE_LABELS, GraderType } from '../../../shared/entities/grader-type.model';
import { GOAL_MODE_LABELS, GoalMode } from '../../../shared/entities/goal-mode.model';
import { ReachabilityReport } from '../../../shared/entities/hint-suggestion.model';
import { MathBuilderComponent } from '../math-builder/math-builder.component';
import { MathDerivationWorkspaceComponent } from '../math-derivation-workspace/math-derivation-workspace.component';

/**
 * Instructor edit UI for a single {@link MathProblem}. The problem is mutated in place — the parent holds the same
 * object reference, so template two-way bindings via {@code [(ngModel)]="problem().field"} write straight through.
 */
@Component({
    selector: 'jhi-math-problem-edit',
    templateUrl: './math-problem-edit.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        MathBuilderComponent,
        MathDerivationWorkspaceComponent,
        ButtonModule,
        CardModule,
        CheckboxModule,
        InputTextModule,
        MessageModule,
        SelectModule,
        TagModule,
        TooltipModule,
    ],
})
export class MathProblemEditComponent {
    private mathExerciseService = inject(MathExerciseService);

    readonly problem = input.required<MathProblem>();
    readonly exerciseId = input<number | undefined>();
    readonly problemChange = output<MathProblem>();

    /** Editor-only preview toggle for the example-derivation workspace (not persisted on the problem). */
    readonly previewOnlyApplicableRules = signal(false);

    readonly reachability = signal<ReachabilityReport | undefined>(undefined);
    readonly reachabilityChecking = signal(false);
    readonly reachabilityError = signal<string | undefined>(undefined);

    readonly graderTypeOptions: { value: GraderType; label: string; disabled: boolean }[] = (Object.keys(GRADER_TYPE_LABELS) as GraderType[]).map((value) => ({
        value,
        label: GRADER_TYPE_LABELS[value],
        disabled: !GRADER_TYPES_AVAILABLE.includes(value),
    }));

    readonly goalModeOptions: { value: GoalMode; label: string }[] = (Object.keys(GOAL_MODE_LABELS) as GoalMode[]).map((value) => ({
        value,
        label: GOAL_MODE_LABELS[value],
    }));

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
        // Reset the example derivation — it's tied to the previous start expression.
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
