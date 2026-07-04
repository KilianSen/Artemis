import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/foundation/service/alert.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { MathProblemAnswer } from 'app/math/shared/entities/math-problem-answer.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise/exercise-headers/with-details/header-exercise-page-with-details.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { ExerciseSubmitButtonComponent } from 'app/exercise/shared/exercise-submit-button/exercise-submit-button.component';
import { BlockDefinitionModel } from 'app/math/shared/entities/block-definition.model';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathProblemParticipationComponent } from 'app/math/participate/math-problem-participation/math-problem-participation.component';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/foundation/constants/exercise-exam-constants';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';

@Component({
    selector: 'jhi-math-submission',
    templateUrl: './math-submission.component.html',
    imports: [
        HeaderExercisePageWithDetailsComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        HtmlForMarkdownPipe,
        ExerciseSubmitButtonComponent,
        MathProblemParticipationComponent,
        CardModule,
        TagModule,
    ],
})
export class MathSubmissionComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private mathSubmissionService = inject(MathSubmissionService);
    private blockRegistryService = inject(MathBlockRegistryService);
    private alertService = inject(AlertService);

    participationId = input<number>();

    readonly mathExercise = signal<MathExercise>(undefined!);
    readonly participation = signal<StudentParticipation>(undefined!);
    readonly submission = signal<MathSubmission>(undefined!);
    readonly result = signal<Result | undefined>(undefined);

    readonly isSaving = signal(false);

    /** The shared block registry, loaded once and passed down to every per-problem editor. */
    readonly blocks = signal<BlockDefinitionModel[]>([]);

    /** Steps the student started each problem with — read once by the child editors on init. Keyed by problem id. */
    readonly initialStepsByProblemId = signal<Map<number, DerivationStep[]>>(new Map());
    /** Per-problem earned points, populated after grading. Keyed by problem id. */
    readonly scoreByProblemId = signal<Map<number, number | undefined>>(new Map());

    /** Latest steps emitted by each child editor. Keyed by problem id — used to assemble answers on save/submit. */
    private collectedSteps = new Map<number, DerivationStep[]>();
    /** Existing answer ids so updates keep the same MathProblemAnswer identity. Keyed by problem id. */
    private existingAnswerIdByProblemId = new Map<number, number>();

    // Autosave state
    hasUnsavedChanges = signal(false);
    lastSavedAt = signal<Date | undefined>(undefined);
    private autosaveInterval: ReturnType<typeof setInterval> | undefined;
    private autosaveTick = 0;

    readonly problems = computed<MathProblem[]>(() => this.mathExercise()?.problems ?? []);

    ngOnInit() {
        const participationIdParam = this.participationId() !== undefined ? this.participationId() : Number(this.route.snapshot.paramMap.get('participationId'));
        if (participationIdParam === undefined || Number.isNaN(participationIdParam)) {
            return this.alertService.error('artemisApp.mathExercise.error');
        }
        const participationId = participationIdParam!;

        this.mathSubmissionService.getDataForMathEditor(participationId).subscribe({
            next: (response) => {
                const submission = response.body as MathSubmission;
                this.submission.set(submission);
                this.participation.set(submission.participation as StudentParticipation);
                this.mathExercise.set(this.participation().exercise as MathExercise);

                const results = submission.results;
                if (results && results.length > 0) {
                    this.result.set(results[results.length - 1]);
                }

                this.syncAnswerState(submission, true);
            },
            error: () => this.alertService.error('artemisApp.mathExercise.error'),
        });

        this.blockRegistryService.getBlockRegistry().subscribe({
            next: (blocks) => this.blocks.set(blocks),
        });

        this.autosaveInterval = setInterval(() => {
            this.autosaveTick++;
            if (this.autosaveTick >= AUTOSAVE_EXERCISE_INTERVAL && this.hasUnsavedChanges() && !this.submission()?.submitted) {
                this.autosaveTick = 0;
                this.save(true);
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    ngOnDestroy() {
        if (this.autosaveInterval !== undefined) {
            clearInterval(this.autosaveInterval);
        }
    }

    /**
     * Rebuilds the per-problem bookkeeping maps from a submission's answers: existing answer ids (for identity-preserving
     * updates) and per-problem earned scores. When {@code seedInitial} is set the collected/initial-step maps are seeded too —
     * done once on load so untouched problems keep their saved steps on the next save.
     */
    private syncAnswerState(submission: MathSubmission, seedInitial = false): void {
        const answers = submission.answers ?? [];
        const existingIds = new Map<number, number>();
        const scores = new Map<number, number | undefined>();
        const initialSteps = new Map<number, DerivationStep[]>();
        for (const answer of answers) {
            if (answer.problemId === undefined) {
                continue;
            }
            if (answer.id !== undefined) {
                existingIds.set(answer.problemId, answer.id);
            }
            scores.set(answer.problemId, answer.scoreInPoints);
            initialSteps.set(answer.problemId, answer.steps ?? []);
        }
        this.existingAnswerIdByProblemId = existingIds;
        this.scoreByProblemId.set(scores);
        if (seedInitial) {
            this.initialStepsByProblemId.set(initialSteps);
            this.collectedSteps = new Map(initialSteps);
        }
    }

    initialStepsFor(problemId: number | undefined): DerivationStep[] {
        return (problemId !== undefined ? this.initialStepsByProblemId().get(problemId) : undefined) ?? [];
    }

    scoreFor(problemId: number | undefined): number | undefined {
        return problemId !== undefined ? this.scoreByProblemId().get(problemId) : undefined;
    }

    onStepsChange(problemId: number | undefined, steps: DerivationStep[]): void {
        if (problemId === undefined) {
            return;
        }
        this.collectedSteps.set(problemId, steps);
        this.hasUnsavedChanges.set(true);
    }

    /** Assembles one answer per problem from the collected steps, reusing existing answer ids where present. */
    private buildAnswers(): MathProblemAnswer[] {
        return this.problems().map((problem) => {
            const answer = new MathProblemAnswer(problem.id);
            if (problem.id !== undefined) {
                answer.id = this.existingAnswerIdByProblemId.get(problem.id);
                answer.steps = this.collectedSteps.get(problem.id) ?? [];
            }
            return answer;
        });
    }

    // ── Save / Submit ─────────────────────────────────────────────────────────

    save(silent = false) {
        const submission = this.submission();
        if (!submission || !this.mathExercise()) {
            return;
        }
        this.isSaving.set(true);
        submission.answers = this.buildAnswers();

        const observable = submission.id
            ? this.mathSubmissionService.update(submission, this.mathExercise().id!)
            : this.mathSubmissionService.create(submission, this.mathExercise().id!);

        observable.subscribe({
            next: (response) => {
                const updated = response.body!;
                this.submission.set(updated);
                this.syncAnswerState(updated);
                this.isSaving.set(false);
                this.hasUnsavedChanges.set(false);
                this.lastSavedAt.set(new Date());
                if (!silent) {
                    this.alertService.success('artemisApp.mathExercise.saveSuccessful');
                }
            },
            error: () => {
                this.isSaving.set(false);
                if (!silent) {
                    this.alertService.error('artemisApp.mathExercise.saveFailed');
                }
            },
        });
    }

    submit() {
        const submission = this.submission();
        if (!submission || !this.mathExercise()) {
            return;
        }
        submission.submitted = true;
        submission.answers = this.buildAnswers();

        const observable = submission.id
            ? this.mathSubmissionService.update(submission, this.mathExercise().id!)
            : this.mathSubmissionService.create(submission, this.mathExercise().id!);

        observable.subscribe({
            next: (response) => {
                const updated = response.body!;
                this.submission.set(updated);
                this.syncAnswerState(updated);
                this.hasUnsavedChanges.set(false);
                this.lastSavedAt.set(new Date());
                this.alertService.success('artemisApp.mathExercise.submitSuccessful');

                if (updated.results && updated.results.length > 0) {
                    this.result.set(updated.results[0]);
                }
            },
            error: () => {
                submission.submitted = false;
                this.alertService.error('artemisApp.mathExercise.submitFailed');
            },
        });
    }
}
