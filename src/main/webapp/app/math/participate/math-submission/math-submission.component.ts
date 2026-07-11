import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';
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
import { ExerciseSubmitButtonComponent } from 'app/exercise/shared/exercise-submit-button/exercise-submit-button.component';
import { BlockDefinitionModel } from 'app/math/shared/entities/block-definition.model';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathProblemParticipationComponent } from 'app/math/participate/math-problem-participation/math-problem-participation.component';
import { MathInductionParticipationComponent } from 'app/math/participate/math-induction-participation/math-induction-participation.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { AccountService } from 'app/core/auth/account.service';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/foundation/constants/exercise-exam-constants';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

@Component({
    selector: 'jhi-math-submission',
    templateUrl: './math-submission.component.html',
    styleUrl: './math-submission.component.scss',
    imports: [
        HeaderExercisePageWithDetailsComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ExerciseSubmitButtonComponent,
        MathProblemParticipationComponent,
        MathInductionParticipationComponent,
        RatingComponent,
        ComplaintsStudentViewComponent,
        ButtonModule,
        MessageModule,
        TagModule,
        TooltipModule,
    ],
})
export class MathSubmissionComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private mathSubmissionService = inject(MathSubmissionService);
    private blockRegistryService = inject(MathBlockRegistryService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);
    private document = inject(DOCUMENT);

    participationId = input<number>();

    readonly mathExercise = signal<MathExercise>(undefined!);
    readonly participation = signal<StudentParticipation>(undefined!);
    readonly submission = signal<MathSubmission>(undefined!);
    readonly result = signal<Result | undefined>(undefined);

    /** Whether the current user owns this participation — gates the rating and complaint widgets (like other exercise types). */
    readonly isOwnerOfParticipation = computed(() => !!this.participation() && this.accountService.isOwnerOfParticipation(this.participation()));

    readonly isSaving = signal(false);

    /** True while a remote grader grades the submitted answer asynchronously; the view polls the submission until the result lands. */
    readonly gradingPending = signal(false);
    /** True while a preliminary result exists but a slow certifier is still to upgrade it (Phase 2b). */
    readonly certifying = signal(false);
    private gradingPollInterval: ReturnType<typeof setInterval> | undefined;
    private gradingPollTries = 0;

    /** The shared block registry, loaded once and passed down to every per-problem editor. */
    readonly blocks = signal<BlockDefinitionModel[]>([]);

    /** Steps the student started each problem with — read once by the child editors on init. Keyed by problem id. */
    readonly initialStepsByProblemId = signal<Map<number, DerivationStep[]>>(new Map());
    /** Per-problem earned points, populated after grading. Keyed by problem id. */
    readonly scoreByProblemId = signal<Map<number, number | undefined>>(new Map());
    /** Ids of problems the student has started (at least one step) — drives the quiz-style problem navigation. */
    readonly answeredProblemIds = signal<Set<number>>(new Set());

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
        this.stopGradingPoll();
    }

    /** Starts polling the submission for the asynchronously-produced result (remote grading). */
    private startGradingPoll(): void {
        this.stopGradingPoll();
        this.gradingPending.set(true);
        this.gradingPollTries = 0;
        this.gradingPollInterval = setInterval(() => this.pollForGradingResult(), 3000);
    }

    private pollForGradingResult(): void {
        const participationId = this.participation()?.id;
        // Give up after ~2 minutes; if the backend is down the submission simply stays for manual review.
        if (participationId === undefined || this.gradingPollTries++ >= 40) {
            this.stopGradingPoll();
            return;
        }
        this.mathSubmissionService.getDataForMathEditor(participationId).subscribe({
            next: (response) => {
                const updated = response.body as MathSubmission;
                this.submission.set(updated);
                this.syncAnswerState(updated);
                const results = updated.results;
                if (results && results.length > 0) {
                    this.result.set(results[results.length - 1]);
                }
                // Keep polling through certification: a slow certifier (Phase 2b) may still upgrade the preliminary result.
                const stillCertifying = (updated.answers ?? []).some((answer) => answer.certificationPending);
                this.certifying.set(stillCertifying);
                if (results && results.length > 0 && !stillCertifying) {
                    this.stopGradingPoll();
                }
            },
        });
    }

    private stopGradingPoll(): void {
        this.gradingPending.set(false);
        this.certifying.set(false);
        if (this.gradingPollInterval !== undefined) {
            clearInterval(this.gradingPollInterval);
            this.gradingPollInterval = undefined;
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
        this.refreshAnsweredProblems();
    }

    initialStepsFor(problemId: number | undefined): DerivationStep[] {
        return (problemId !== undefined ? this.initialStepsByProblemId().get(problemId) : undefined) ?? [];
    }

    scoreFor(problemId: number | undefined): number | undefined {
        return problemId !== undefined ? this.scoreByProblemId().get(problemId) : undefined;
    }

    /** Whether the student has started this problem (at least one derivation step). */
    isAnswered(problemId: number | undefined): boolean {
        return problemId !== undefined && this.answeredProblemIds().has(problemId);
    }

    /** PrimeNG severity for a problem's navigation button: graded → success/danger, else started → primary, else secondary. */
    navSeverity(problem: MathProblem): 'success' | 'danger' | 'primary' | 'secondary' {
        const score = this.scoreFor(problem.id);
        if (this.submission()?.submitted && score !== undefined) {
            return score >= (problem.points ?? 0) ? 'success' : 'danger';
        }
        return this.isAnswered(problem.id) ? 'primary' : 'secondary';
    }

    /** A problem's navigation button is outlined until it is started (or when it lost points after grading). */
    navOutlined(problem: MathProblem): boolean {
        const score = this.scoreFor(problem.id);
        if (this.submission()?.submitted && score !== undefined) {
            return score < (problem.points ?? 0);
        }
        return !this.isAnswered(problem.id);
    }

    /** Scrolls the given problem into view — used by the quiz-style problem navigation. */
    navigateToProblem(index: number): void {
        this.document.getElementById('math-problem-' + index)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    /** Recomputes which problems have at least one step, from the collected steps. */
    private refreshAnsweredProblems(): void {
        const answered = new Set<number>();
        this.collectedSteps.forEach((steps, problemId) => {
            if (steps.length > 0) {
                answered.add(problemId);
            }
        });
        this.answeredProblemIds.set(answered);
    }

    onStepsChange(problemId: number | undefined, steps: DerivationStep[]): void {
        if (problemId === undefined) {
            return;
        }
        this.collectedSteps.set(problemId, steps);
        this.refreshAnsweredProblems();
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
                } else {
                    // No result yet — a remote grader is grading asynchronously; poll the submission until it lands.
                    this.startGradingPoll();
                }
            },
            error: () => {
                submission.submitted = false;
                this.alertService.error('artemisApp.mathExercise.submitFailed');
            },
        });
    }
}
