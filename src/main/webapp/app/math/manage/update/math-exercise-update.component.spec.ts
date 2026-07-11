import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MathExerciseUpdateComponent } from 'app/math/manage/update/math-exercise-update.component';
import { MathExerciseService } from 'app/math/manage/service/math-exercise.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';

describe('MathExerciseUpdateComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathExerciseUpdateComponent;
    let mathExerciseService: { create: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn>; verifyReachability: ReturnType<typeof vi.fn> };
    let router: { navigate: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        mathExerciseService = {
            create: vi.fn().mockReturnValue(of({ body: new MathExercise(undefined) })),
            update: vi.fn().mockReturnValue(of({ body: new MathExercise(undefined) })),
            verifyReachability: vi.fn().mockReturnValue(of(undefined)),
        };
        router = { navigate: vi.fn() };
        const exercise = new MathExercise(undefined);

        TestBed.configureTestingModule({
            imports: [MathExerciseUpdateComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: MathExerciseService, useValue: mathExerciseService },
                { provide: Router, useValue: router },
                MockProvider(ExerciseService, { validateDate: vi.fn() }),
                MockProvider(ProfileService, { isDevelopment: () => false }),
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
                { provide: ActivatedRoute, useValue: { data: of({ mathExercise: exercise }), snapshot: { params: { courseId: 7 }, url: [] } } },
            ],
        }).overrideComponent(MathExerciseUpdateComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(MathExerciseUpdateComponent);
        component = fixture.componentInstance;
        component.ngOnInit();
    });

    it('initialises with problems defaulted to an empty array', () => {
        expect(component.mathExercise).toBeTruthy();
        expect(component.mathExercise.problems).toEqual([]);
        expect(component.isSaving()).toBe(false);
    });

    it('addProblem pushes a new MathProblem', () => {
        component.addProblem();
        expect(component.mathExercise.problems).toHaveLength(1);
        expect(component.mathExercise.problems![0]).toBeInstanceOf(MathProblem);
    });

    it('removeProblem drops the entry at the given index', () => {
        component.addProblem();
        component.addProblem();
        component.mathExercise.problems![0].title = 'keep';
        component.removeProblem(1);
        expect(component.mathExercise.problems).toHaveLength(1);
        expect(component.mathExercise.problems![0].title).toBe('keep');
    });

    it('moveProblemUp swaps a problem with its predecessor', () => {
        component.addProblem();
        component.addProblem();
        component.mathExercise.problems![0].title = 'first';
        component.mathExercise.problems![1].title = 'second';
        component.moveProblemUp(1);
        expect(component.mathExercise.problems![0].title).toBe('second');
        expect(component.mathExercise.problems![1].title).toBe('first');
    });

    it('moveProblemUp is a no-op for the first problem', () => {
        component.addProblem();
        component.mathExercise.problems![0].title = 'only';
        component.moveProblemUp(0);
        expect(component.mathExercise.problems![0].title).toBe('only');
    });

    it('moveProblemDown swaps a problem with its successor', () => {
        component.addProblem();
        component.addProblem();
        component.mathExercise.problems![0].title = 'first';
        component.mathExercise.problems![1].title = 'second';
        component.moveProblemDown(0);
        expect(component.mathExercise.problems![0].title).toBe('second');
        expect(component.mathExercise.problems![1].title).toBe('first');
    });

    it('moveProblemDown is a no-op for the last problem', () => {
        component.addProblem();
        component.mathExercise.problems![0].title = 'only';
        component.moveProblemDown(0);
        expect(component.mathExercise.problems![0].title).toBe('only');
    });

    it('a fresh exercise has zero total points (Save guard active) that a default problem clears', () => {
        // The template disables Save and shows the pointsRequired hint while totalPoints <= 0, so an instructor
        // can no longer trip the backend "max points needs to be greater than 0" error by saving an empty exercise.
        expect(component.totalPoints).toBe(0);
        component.addProblem();
        expect(component.totalPoints).toBe(1); // new MathProblem defaults to 1 point → guard clears
    });

    it('totalPoints sums the points of all problems', () => {
        component.addProblem();
        component.addProblem();
        component.mathExercise.problems![0].points = 3;
        component.mathExercise.problems![1].points = 4;
        expect(component.totalPoints).toBe(7);
    });

    it('save() writes the summed problem points to maxPoints and routes through create when the exercise has no id', () => {
        component.addProblem();
        component.mathExercise.problems![0].points = 5;
        component.save();
        expect(component.mathExercise.maxPoints).toBe(5);
        expect(mathExerciseService.create).toHaveBeenCalled();
    });

    it('save() routes through update when the exercise has an id', () => {
        component.mathExercise.id = 5;
        component.save();
        expect(mathExerciseService.update).toHaveBeenCalled();
    });
});
