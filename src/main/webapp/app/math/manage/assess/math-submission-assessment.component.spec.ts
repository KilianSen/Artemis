import { describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MathSubmissionAssessmentComponent } from 'app/math/manage/assess/math-submission-assessment.component';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

describe('MathSubmissionAssessmentComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathSubmissionAssessmentComponent;
    let mathSubmissionService: MathSubmissionService;

    const buildComponent = (submissionIdParam: string, exercise: MathExercise, submission: MathSubmission) => {
        TestBed.configureTestingModule({
            imports: [MathSubmissionAssessmentComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(MathBlockRegistryService, { getBlockRegistry: () => of([]) as any }),
                MockProvider(MathSubmissionService, {
                    saveManualResult: vi.fn().mockReturnValue(of(submission)),
                    getSubmissionWithoutAssessment: vi.fn().mockReturnValue(of(submission)),
                    cancelAssessment: vi.fn().mockReturnValue(of(undefined)),
                }),
                MockProvider(AlertService),
                MockProvider(Router),
                MockProvider(Location),
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: of({ mathSubmission: submission }),
                        snapshot: {
                            params: { courseId: 9 },
                            parent: null,
                            paramMap: convertToParamMap({ exerciseId: '11', submissionId: submissionIdParam }),
                            queryParamMap: convertToParamMap({}),
                        },
                    },
                },
            ],
        }).overrideComponent(MathSubmissionAssessmentComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(MathSubmissionAssessmentComponent);
        mathSubmissionService = TestBed.inject(MathSubmissionService);
        component = fixture.componentInstance;
        component.ngOnInit();
    };

    const mockData = () => {
        const exercise = new MathExercise(undefined);
        exercise.id = 11;
        const participation = { id: 1, exercise } as StudentParticipation;
        const submission = new MathSubmission();
        submission.id = 5;
        submission.participation = participation;
        submission.results = [{ score: 80 } as any];
        return { exercise, submission };
    };

    it('hydrates exercise/submission/result from the resolver data', () => {
        const { exercise, submission } = mockData();
        buildComponent('5', exercise, submission);
        expect(component.submission().id).toBe(5);
        expect(component.mathExercise().id).toBe(11);
        expect(component.result()?.score).toBe(80);
        expect(component.manualScore).toBe(80);
        expect(component.isLoading()).toBe(false);
    });

    it('extracts courseId from the route snapshot', () => {
        const { exercise, submission } = mockData();
        buildComponent('5', exercise, submission);
        expect(component.courseId()).toBe(9);
    });

    it('allows overriding while there is no assessment due date', () => {
        const { exercise, submission } = mockData();
        buildComponent('5', exercise, submission);
        expect(component.hasAssessmentDueDatePassed()).toBe(false);
        expect(component.canOverride()).toBe(true);
        expect(component.readOnly()).toBe(false);
    });

    it('is read-only after the assessment due date has passed', () => {
        const { exercise, submission } = mockData();
        exercise.assessmentDueDate = dayjs().subtract(1, 'hour');
        buildComponent('5', exercise, submission);
        expect(component.hasAssessmentDueDatePassed()).toBe(true);
        expect(component.canOverride()).toBe(false);
        expect(component.readOnly()).toBe(true);
    });

    it('submits a final assessment with submit=true', () => {
        const { exercise, submission } = mockData();
        buildComponent('5', exercise, submission);
        component.manualScore = 75;

        component.submitAssessment();

        expect(mathSubmissionService.saveManualResult).toHaveBeenCalledWith(5, 75, expect.anything(), true);
    });

    it('saves a draft with submit=false', () => {
        const { exercise, submission } = mockData();
        buildComponent('5', exercise, submission);
        component.manualScore = 40;

        component.saveAssessment();

        expect(mathSubmissionService.saveManualResult).toHaveBeenCalledWith(5, 40, expect.anything(), false);
    });

    it('locks and loads the next submission when opened as "new"', () => {
        const { exercise, submission } = mockData();
        buildComponent('new', exercise, submission);

        expect(mathSubmissionService.getSubmissionWithoutAssessment).toHaveBeenCalledWith(11, true, 0);
        expect(component.submission().id).toBe(5);
    });
});
