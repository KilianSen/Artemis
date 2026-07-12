import { beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { MathSubmissionComponent } from 'app/math/participate/math-submission/math-submission.component';
import { MathProblemParticipationComponent } from 'app/math/participate/math-problem-participation/math-problem-participation.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { AccountService } from 'app/core/auth/account.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { MathGradingStatusMessage } from 'app/math/shared/entities/math-submission.model';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise/exercise-headers/with-details/header-exercise-page-with-details.component';
import { ExerciseSubmitButtonComponent } from 'app/exercise/shared/exercise-submit-button/exercise-submit-button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';

describe('MathSubmissionComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathSubmissionComponent;
    let fixture: ComponentFixture<MathSubmissionComponent>;
    let mathSubmissionService: MathSubmissionService;
    let alertService: AlertService;
    let resultSubject: BehaviorSubject<Result | undefined>;
    let gradingStatusSubject: Subject<MathGradingStatusMessage>;

    const mockExercise = (): MathExercise => {
        const ex = new MathExercise(undefined);
        ex.id = 10;
        ex.type = ExerciseType.MATH;
        const problem = new MathProblem();
        problem.id = 1;
        problem.points = 5;
        problem.goalMode = 'TRANSFORMATION';
        ex.problems = [problem];
        return ex;
    };

    const OWNER_LOGIN = 'artemis_test_user_1';

    // The math-editor DTO projects the owner as a flat studentLogin (no nested student), mirrored here.
    const mockParticipation = (exercise: MathExercise): StudentParticipation => {
        return { id: 42, exercise, studentLogin: OWNER_LOGIN } as unknown as StudentParticipation;
    };

    const mockSubmission = (participation: StudentParticipation): MathSubmission => {
        const sub = new MathSubmission();
        sub.id = 5;
        sub.submitted = false;
        sub.participation = participation;
        return sub;
    };

    beforeEach(() => {
        resultSubject = new BehaviorSubject<Result | undefined>(undefined);
        gradingStatusSubject = new Subject<MathGradingStatusMessage>();
        TestBed.configureTestingModule({
            imports: [MathSubmissionComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                MockProvider(AccountService, { userIdentity: signal({ login: OWNER_LOGIN } as any) }),
                MockProvider(MathSubmissionService),
                MockProvider(ParticipationWebsocketService, {
                    subscribeForLatestResultOfParticipation: () => resultSubject as any,
                    unsubscribeForLatestResultOfParticipation: () => {},
                }),
                MockProvider(WebsocketService, { subscribe: () => gradingStatusSubject as any }),
                MockProvider(MathBlockRegistryService, { getBlockRegistry: () => of([]) as any }),
                MockProvider(TranslateService, {
                    instant: (key: string) => key,
                    get: (key: string) => of(key) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ participationId: '42' }) },
                    },
                },
            ],
            declarations: [],
        }).overrideComponent(MathSubmissionComponent, {
            remove: {
                imports: [
                    HeaderExercisePageWithDetailsComponent,
                    ExerciseSubmitButtonComponent,
                    MathProblemParticipationComponent,
                    RatingComponent,
                    ComplaintsStudentViewComponent,
                ],
            },
            add: {
                imports: [
                    MockComponent(HeaderExercisePageWithDetailsComponent),
                    MockComponent(ExerciseSubmitButtonComponent),
                    MockComponent(MathProblemParticipationComponent),
                    MockComponent(RatingComponent),
                    MockComponent(ComplaintsStudentViewComponent),
                    MockDirective(TranslateDirective),
                    MockPipe(ArtemisTranslatePipe),
                    MockPipe(HtmlForMarkdownPipe),
                ],
            },
        });

        fixture = TestBed.createComponent(MathSubmissionComponent);
        component = fixture.componentInstance;
        mathSubmissionService = TestBed.inject(MathSubmissionService);
        alertService = TestBed.inject(AlertService);
    });

    it('should load participation and existing submission on init', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        submission.answers = [{ id: 7, problemId: 1, steps: [] }];
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));

        fixture.detectChanges();

        expect(component.mathExercise()).toBe(exercise);
        expect(component.participation()).toBe(participation);
        expect(component.submission()).toBe(submission);
    });

    it('should derive participation ownership from the flat studentLogin without throwing', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));

        // Rendering must not throw even though the participation carries no nested student/team (regression: the
        // previous AccountService.isOwnerOfParticipation call threw "Participation does not have any owners" and
        // crashed the whole editor for a student's own participation).
        expect(() => fixture.detectChanges()).not.toThrow();
        expect(component.isOwnerOfParticipation()).toBe(true);
    });

    it('should not claim ownership when the studentLogin does not match the current user', () => {
        const exercise = mockExercise();
        const participation = { id: 42, exercise, studentLogin: 'someone-else' } as unknown as StudentParticipation;
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));

        fixture.detectChanges();

        expect(component.isOwnerOfParticipation()).toBe(false);
    });

    it('should show error alert when loading fails', () => {
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(throwError(() => new Error('network')));
        const errorSpy = vi.spyOn(alertService, 'error');

        fixture.detectChanges();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.mathExercise.error');
    });

    it('should call create when saving a new submission', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = new MathSubmission();
        submission.participation = participation;
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const createSpy = vi.spyOn(mathSubmissionService, 'create').mockReturnValue(of(new HttpResponse({ body: submission })));
        fixture.detectChanges();

        component.save();

        expect(createSpy).toHaveBeenCalled();
        expect(submission.answers).toBeDefined();
        expect(submission.answers!.length).toBe(1);
        expect(submission.answers![0].problemId).toBe(1);
    });

    it('should call update when saving an existing submission', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const updateSpy = vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        fixture.detectChanges();

        component.save();

        expect(updateSpy).toHaveBeenCalled();
    });

    it('should set submitted=true and call update on submit', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const submittedSub = { ...submission, submitted: true, results: [{ score: 100 }] };
        const updateSpy = vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submittedSub as any })));
        fixture.detectChanges();

        component.submit();

        expect(updateSpy).toHaveBeenCalled();
        expect(component.submission().submitted).toBe(true);
        expect(component.result()?.score).toBe(100);
    });

    it('should refresh per-problem scores from answers after submit', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const submittedSub = { ...submission, submitted: true, results: [{ score: 80 }], answers: [{ id: 9, problemId: 1, scoreInPoints: 4, steps: [] }] };
        vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submittedSub as any })));
        fixture.detectChanges();

        component.submit();

        expect(component.scoreFor(1)).toBe(4);
    });

    it('should mark grading pending on submit when no result is returned yet', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const submittedSub = { ...submission, submitted: true };
        vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submittedSub as any })));
        fixture.detectChanges();

        component.submit();

        expect(component.gradingPending()).toBe(true);
        expect(component.result()).toBeUndefined();
    });

    it('should apply a pushed websocket result and clear the pending indicator', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const submittedSub = { ...submission, submitted: true };
        vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submittedSub as any })));
        fixture.detectChanges();
        component.submit();
        expect(component.gradingPending()).toBe(true);

        // The remote grader pushes the authoritative result over the websocket subscription.
        resultSubject.next({ id: 3, score: 100, rated: true } as Result);

        expect(component.result()?.score).toBe(100);
        expect(component.gradingPending()).toBe(false);
        expect(component.certifying()).toBe(false);
    });

    it('should ignore unrated pushed results', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        fixture.detectChanges();

        resultSubject.next({ id: 4, score: 50, rated: false } as Result);

        expect(component.result()).toBeUndefined();
    });

    it('should keep the certifying indicator until the certification push arrives', () => {
        const exercise = mockExercise();
        exercise.problems![0].certifyingGraderType = 'LEANREGATE' as any;
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const submittedSub = { ...submission, submitted: true };
        vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submittedSub as any })));
        fixture.detectChanges();
        component.submit();

        // Preliminary push: a certifier is configured, so certifying stays true.
        resultSubject.next({ id: 5, score: 100, rated: true } as Result);
        expect(component.certifying()).toBe(true);

        // Certification push: the certifying indicator clears.
        resultSubject.next({ id: 5, score: 100, rated: true } as Result);
        expect(component.certifying()).toBe(false);
    });

    it('should enter the under-review state on a pushed REVIEW grading status', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const submittedSub = { ...submission, submitted: true };
        vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submittedSub as any })));
        fixture.detectChanges();
        component.submit();
        expect(component.gradingPending()).toBe(true);

        // The server pushes a REVIEW status when automatic grading is inconclusive.
        gradingStatusSubject.next({ submissionId: 5, participationId: 42, status: 'REVIEW' });

        expect(component.underReview()).toBe(true);
        expect(component.gradingPending()).toBe(false);
        expect(component.result()).toBeUndefined();
    });

    it('should ignore grading-status pushes for a different participation', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        fixture.detectChanges();

        gradingStatusSubject.next({ submissionId: 99, participationId: 999, status: 'REVIEW' });

        expect(component.underReview()).toBe(false);
    });

    it('should reconstruct the under-review state from the loaded submission gradingState', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        submission.submitted = true;
        submission.gradingState = 'REVIEW';
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));

        fixture.detectChanges();

        expect(component.underReview()).toBe(true);
        expect(component.gradingPending()).toBe(false);
    });

    it('should revert submitted=false when submit fails', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        vi.spyOn(mathSubmissionService, 'update').mockReturnValue(throwError(() => new Error('server error')));
        fixture.detectChanges();

        component.submit();

        expect(component.submission().submitted).toBe(false);
    });
});
