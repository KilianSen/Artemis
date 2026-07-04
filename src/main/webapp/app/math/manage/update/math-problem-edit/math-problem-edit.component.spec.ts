import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MathProblemEditComponent } from 'app/math/manage/update/math-problem-edit/math-problem-edit.component';
import { MathExerciseService } from 'app/math/manage/service/math-exercise.service';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { MathNode } from 'app/math/shared/entities/math-node.model';

describe('MathProblemEditComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathProblemEditComponent;
    let fixture: ComponentFixture<MathProblemEditComponent>;
    let mathExerciseService: { verifyReachability: ReturnType<typeof vi.fn> };

    function createComponent(problem: MathProblem, exerciseId?: number) {
        fixture = TestBed.createComponent(MathProblemEditComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('problem', problem);
        if (exerciseId !== undefined) {
            fixture.componentRef.setInput('exerciseId', exerciseId);
        }
    }

    beforeEach(() => {
        mathExerciseService = {
            verifyReachability: vi.fn().mockReturnValue(of(undefined)),
        };

        TestBed.configureTestingModule({
            imports: [MathProblemEditComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: MathExerciseService, useValue: mathExerciseService },
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
            ],
        }).overrideComponent(MathProblemEditComponent, { set: { imports: [], template: '' } });
    });

    it('exposes the bound problem input', () => {
        const problem = new MathProblem();
        createComponent(problem);
        expect(component.problem()).toBe(problem);
    });

    it('onSourceExpressionChange updates the source and resets the example derivation', () => {
        const problem = new MathProblem();
        problem.exampleDerivations = [{} as any];
        createComponent(problem);
        const node = {} as MathNode;
        component.onSourceExpressionChange(node);
        expect(problem.sourceExpression).toBe(node);
        expect(problem.exampleDerivations).toEqual([]);
    });

    it('onGoalExpressionChange updates the goal and resets the example derivation', () => {
        const problem = new MathProblem();
        problem.exampleDerivations = [{} as any];
        createComponent(problem);
        const node = {} as MathNode;
        component.onGoalExpressionChange(node);
        expect(problem.goalExpression).toBe(node);
        expect(problem.exampleDerivations).toEqual([]);
    });

    it('onGoalModeChange updates the mode and resets the example derivation', () => {
        const problem = new MathProblem();
        problem.exampleDerivations = [{} as any];
        createComponent(problem);
        component.onGoalModeChange('EQUATION');
        expect(problem.goalMode).toBe('EQUATION');
        expect(problem.exampleDerivations).toEqual([]);
    });

    it('onTargetExpressionChange updates the target without clearing the example derivation', () => {
        const problem = new MathProblem();
        problem.exampleDerivations = [{} as any];
        createComponent(problem);
        const node = {} as MathNode;
        component.onTargetExpressionChange(node);
        expect(problem.targetExpression).toBe(node);
        expect(problem.exampleDerivations).toHaveLength(1);
    });

    it('checkReachability sets the saveFirst key when the exercise is unsaved', () => {
        const problem = new MathProblem();
        problem.id = 3;
        createComponent(problem);
        component.checkReachability();
        expect(component.reachabilityError()).toBe('artemisApp.mathExercise.reachability.saveFirst');
        expect(mathExerciseService.verifyReachability).not.toHaveBeenCalled();
    });

    it('checkReachability sets the saveFirst key when the problem has no id', () => {
        const problem = new MathProblem();
        createComponent(problem, 42);
        component.checkReachability();
        expect(component.reachabilityError()).toBe('artemisApp.mathExercise.reachability.saveFirst');
        expect(mathExerciseService.verifyReachability).not.toHaveBeenCalled();
    });

    it('checkReachability calls the service with both ids when the exercise and problem are saved', () => {
        const problem = new MathProblem();
        problem.id = 3;
        createComponent(problem, 42);
        component.checkReachability();
        expect(mathExerciseService.verifyReachability).toHaveBeenCalledWith(42, 3);
    });
});
