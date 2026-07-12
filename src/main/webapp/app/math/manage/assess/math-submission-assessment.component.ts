import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe, Location, NgClass } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { getLinkToSubmissionAssessment } from 'app/foundation/util/navigation.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { MathProblemAnswer } from 'app/math/shared/entities/math-problem-answer.model';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { BlockDefinitionModel } from 'app/math/shared/entities/block-definition.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';
import { MathNode, isTautology, mathNodesEqual } from 'app/math/shared/entities/math-node.model';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { MathNodeLatexPipe } from 'app/math/shared/math-node-latex.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { AssessmentAfterComplaint } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { CardModule } from 'primeng/card';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';

@Component({
    selector: 'jhi-math-submission-assessment',
    templateUrl: './math-submission-assessment.component.html',
    styleUrl: './math-submission-assessment.component.scss',
    imports: [
        AssessmentLayoutComponent,
        UnreferencedFeedbackComponent,
        TranslateDirective,
        HtmlForMarkdownPipe,
        MathNodeLatexPipe,
        NgClass,
        DecimalPipe,
        ArtemisDatePipe,
        FormsModule,
        CardModule,
        InputGroupModule,
        InputGroupAddonModule,
        InputTextModule,
        TagModule,
    ],
})
export class MathSubmissionAssessmentComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private location = inject(Location);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private blockRegistryService = inject(MathBlockRegistryService);
    private mathSubmissionService = inject(MathSubmissionService);
    private complaintService = inject(ComplaintService);

    readonly mathExercise = signal<MathExercise>(undefined!);
    readonly submission = signal<MathSubmission>(undefined!);
    readonly result = signal<Result | undefined>(undefined);
    /** The student's complaint on this assessment, if any — drives the complaint panel in the assessment layout. */
    readonly complaint = signal<Complaint | undefined>(undefined);
    readonly courseId = signal<number>(-1);
    private exerciseId = -1;
    private correctionRound = 0;

    readonly isLoading = signal<boolean>(true);
    readonly saveBusy = signal<boolean>(false);
    readonly submitBusy = signal<boolean>(false);
    readonly cancelBusy = signal<boolean>(false);
    readonly nextSubmissionBusy = signal<boolean>(false);
    manualScore: number | undefined;
    /** The tutor's free-text (unreferenced) feedback comments, round-tripped with the manual result. */
    readonly unreferencedFeedback = signal<Feedback[]>([]);
    readonly saveSuccess = signal<boolean>(false);
    blocks = signal<BlockDefinitionModel[]>([]);

    /** Whether the exercise's assessment due date has passed — after which the assessment is read-only (no override in the basic flow). */
    readonly hasAssessmentDueDatePassed = computed<boolean>(() => {
        const due = this.mathExercise()?.assessmentDueDate;
        return !!due && dayjs(due).isBefore(dayjs());
    });

    /** A tutor may create/override the manual assessment until the assessment due date passes. */
    readonly canOverride = computed<boolean>(() => !this.hasAssessmentDueDatePassed());

    /** The manual score input is editable only while overriding is allowed. */
    readonly readOnly = computed<boolean>(() => !this.canOverride());

    /** Memoized rule-id → display-name lookup, recomputed only when the block registry changes (avoids a per-row nested scan on every change-detection cycle). */
    ruleNameById = computed<Map<string, string>>(() => {
        const map = new Map<string, string>();
        for (const block of this.blocks()) {
            for (const rule of block.rules ?? []) {
                map.set(rule.id, rule.name);
            }
        }
        return map;
    });

    /** The ordered problems of the exercise under review. */
    readonly problems = computed<MathProblem[]>(() => this.mathExercise()?.problems ?? []);

    /** Aggregate points earned across all answered problems. */
    readonly earnedPoints = computed<number>(() => (this.submission()?.answers ?? []).reduce((sum, answer) => sum + (answer.scoreInPoints ?? 0), 0));

    /** Sum of the exercise's problem points. */
    readonly maxPoints = computed<number>(() => this.problems().reduce((sum, problem) => sum + (problem.points ?? 0), 0));

    ngOnInit() {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.correctionRound = Number(this.route.snapshot.queryParamMap.get('correction-round') ?? 0);
        const submissionIdParam = this.route.snapshot.paramMap.get('submissionId');

        // Walk up the route tree to find courseId
        let snapshot = this.route.snapshot;
        while (snapshot) {
            if (snapshot.params['courseId']) {
                this.courseId.set(Number(snapshot.params['courseId']));
                break;
            }
            snapshot = snapshot.parent!;
        }

        this.blockRegistryService.getBlockRegistry().subscribe({
            next: (blocks) => this.blocks.set(blocks),
        });

        if (submissionIdParam === 'new') {
            // Started from the assessment dashboard: fetch and lock the next assessable submission.
            this.loadAndLockNextSubmission();
        } else {
            this.route.data.subscribe(({ mathSubmission }) => {
                if (mathSubmission) {
                    this.initFromSubmission(mathSubmission as MathSubmission);
                }
            });
        }
    }

    /** Populates the view from a loaded submission (its exercise, latest result, feedback, and any draft score). */
    private initFromSubmission(submission: MathSubmission): void {
        this.submission.set(submission);
        this.mathExercise.set(submission.participation?.exercise as MathExercise);
        const latest = submission.results?.[submission.results.length - 1];
        this.result.set(latest);
        this.unreferencedFeedback.set(latest?.feedbacks ?? []);
        // Pre-fill the input with an existing draft/submitted score so the tutor can adjust it.
        this.manualScore = latest?.score;
        this.isLoading.set(false);
        this.loadComplaint(submission.id!);
    }

    /** Loads any student complaint on this submission so the assessment layout can show the complaint-response panel. */
    private loadComplaint(submissionId: number): void {
        this.complaintService.findBySubmissionId(submissionId).subscribe({
            next: (res) => {
                if (res.body) {
                    this.complaint.set(this.complaintService.convertComplaintFromServer(res.body, this.result()));
                }
            },
        });
    }

    /**
     * Resolves the student complaint with the tutor's response and the (possibly revised) manual score + feedback.
     * Delegates the accept/reject bookkeeping to the shared complaint-response service on the server.
     */
    onUpdateAfterComplaint(event: AssessmentAfterComplaint): void {
        if (this.manualScore == undefined) {
            this.alertService.error('artemisApp.mathExercise.assessment.invalidScore');
            event.onError();
            return;
        }
        const complaintResponse = this.complaintService.getComplaintResponseForUpdateAfterComplaint(event.complaintResponse);
        this.mathSubmissionService.updateAssessmentAfterComplaint(this.submission().id!, this.manualScore, this.unreferencedFeedback(), complaintResponse).subscribe({
            next: (updated) => {
                this.result.set(updated.results?.[updated.results.length - 1]);
                this.unreferencedFeedback.set(this.result()?.feedbacks ?? []);
                event.onSuccess();
            },
            error: () => event.onError(),
        });
    }

    /** Fetches and locks the next assessable submission, rewriting the URL to its id (mirrors the other assessment editors). */
    private loadAndLockNextSubmission(): void {
        this.mathSubmissionService.getSubmissionWithoutAssessment(this.exerciseId, true, this.correctionRound).subscribe({
            next: (submission) => {
                if (!submission) {
                    this.alertService.error('artemisApp.mathExercise.assessment.noSubmissions');
                    this.router.navigate(['/course-management', this.courseId(), 'assessment-dashboard', this.exerciseId]);
                    return;
                }
                // Replace 'new' in the URL with the actual submission id so a refresh reopens the same submission.
                this.location.replaceState(this.router.serializeUrl(this.router.createUrlTree(this.assessmentLink(submission.id!))));
                this.initFromSubmission(submission);
            },
            error: () => {
                this.alertService.error('artemisApp.mathExercise.assessment.loadFailed');
                this.isLoading.set(false);
            },
        });
    }

    private assessmentLink(submissionId: number | 'new'): string[] {
        return getLinkToSubmissionAssessment(ExerciseType.MATH, this.courseId(), this.exerciseId, undefined, submissionId, undefined, undefined);
    }

    private scoreIsValid(): boolean {
        return this.manualScore != undefined && this.manualScore >= 0 && this.manualScore <= 100;
    }

    /** Saves a draft assessment (keeps the lock, stays hidden from the student). */
    saveAssessment(): void {
        this.persist(false, this.saveBusy);
    }

    /** Submits the final assessment (visible to the student per the assessment due date). */
    submitAssessment(): void {
        this.persist(true, this.submitBusy);
    }

    private persist(submit: boolean, busy: typeof this.saveBusy): void {
        if (!this.scoreIsValid()) {
            this.alertService.error('artemisApp.mathExercise.assessment.invalidScore');
            return;
        }
        busy.set(true);
        this.saveSuccess.set(false);
        this.mathSubmissionService.saveManualResult(this.submission().id!, this.manualScore!, this.unreferencedFeedback(), submit).subscribe({
            next: (updated) => {
                this.result.set(updated.results?.[updated.results.length - 1]);
                this.unreferencedFeedback.set(this.result()?.feedbacks ?? []);
                busy.set(false);
                this.saveSuccess.set(true);
            },
            error: () => busy.set(false),
        });
    }

    /** Cancels the assessment, releasing the lock so another tutor can pick the submission up. */
    cancelAssessment(): void {
        if (!window.confirm(this.translateService.instant('artemisApp.mathExercise.assessment.cancelConfirm'))) {
            return;
        }
        this.cancelBusy.set(true);
        this.mathSubmissionService.cancelAssessment(this.submission().id!).subscribe({
            next: () => {
                this.cancelBusy.set(false);
                this.router.navigate(['/course-management', this.courseId(), 'assessment-dashboard', this.exerciseId]);
            },
            error: () => this.cancelBusy.set(false),
        });
    }

    /** Fetches the next assessable submission and navigates to it, or back to the dashboard when the queue is empty. */
    assessNext(): void {
        this.nextSubmissionBusy.set(true);
        this.mathSubmissionService.getSubmissionWithoutAssessment(this.exerciseId, false, this.correctionRound).subscribe({
            next: (submission) => {
                this.nextSubmissionBusy.set(false);
                if (!submission) {
                    this.alertService.info('artemisApp.mathExercise.assessment.noMoreSubmissions');
                    this.router.navigate(['/course-management', this.courseId(), 'assessment-dashboard', this.exerciseId]);
                    return;
                }
                this.router.navigate(this.assessmentLink(submission.id!));
            },
            error: () => this.nextSubmissionBusy.set(false),
        });
    }

    /** The student's answer for a given problem, if any. */
    answerForProblem(problem: MathProblem): MathProblemAnswer | undefined {
        return (this.submission()?.answers ?? []).find((answer) => answer.problemId === problem.id);
    }

    /** The expression a problem's derivation starts from. */
    startExpression(problem: MathProblem): MathNode | undefined {
        return (problem.goalMode ?? 'TRANSFORMATION') === 'EQUATION' ? problem.goalExpression : problem.sourceExpression;
    }

    hasExpressions(problem: MathProblem): boolean {
        if ((problem.goalMode ?? 'TRANSFORMATION') === 'EQUATION') {
            return !!problem.goalExpression;
        }
        return !!(problem.sourceExpression && problem.targetExpression);
    }

    /** Whether a list of steps reaches the problem's goal (target expression, or tautology in EQUATION mode). */
    isDerivationComplete(problem: MathProblem, steps: DerivationStep[] | undefined): boolean {
        if (!steps?.length) return false;
        const last = steps[steps.length - 1].resultExpression;
        if ((problem.goalMode ?? 'TRANSFORMATION') === 'EQUATION') {
            return isTautology(last);
        }
        const target = problem.targetExpression;
        return !!target && mathNodesEqual(last, target);
    }
}
