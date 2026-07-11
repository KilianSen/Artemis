import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MathProblemParticipationComponent } from 'app/math/participate/math-problem-participation/math-problem-participation.component';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { MathNode } from 'app/math/shared/entities/math-node.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';
import { MathExpressionCanvasComponent } from 'app/math/shared/expression-canvas/math-expression-canvas.component';
import { MathBuilderComponent } from 'app/math/manage/update/math-builder/math-builder.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MathNodeLatexPipe } from 'app/math/shared/math-node-latex.pipe';
import { KatexStringPipe } from 'app/math/shared/katex-string.pipe';

describe('MathProblemParticipationComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathProblemParticipationComponent;
    let fixture: ComponentFixture<MathProblemParticipationComponent>;
    let mathSubmissionService: MathSubmissionService;

    const source: MathNode = { type: 'number', value: '1' };
    const other: MathNode = { type: 'number', value: '2' };

    const mockProblem = (overrides: Partial<MathProblem> = {}): MathProblem => {
        const problem = new MathProblem();
        problem.id = 1;
        problem.points = 5;
        problem.goalMode = 'TRANSFORMATION';
        problem.sourceExpression = source;
        problem.targetExpression = other;
        Object.assign(problem, overrides);
        return problem;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MathProblemParticipationComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(MathSubmissionService),
                MockProvider(TranslateService, {
                    instant: (key: string) => key,
                    get: (key: string) => of(key) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
            ],
        }).overrideComponent(MathProblemParticipationComponent, {
            // Remove the real pipes/directive before adding mock versions, else two pipes share a name (NG0313).
            remove: { imports: [MathExpressionCanvasComponent, MathBuilderComponent, TranslateDirective, ArtemisTranslatePipe, MathNodeLatexPipe, KatexStringPipe] },
            add: {
                imports: [
                    MockComponent(MathExpressionCanvasComponent),
                    MockComponent(MathBuilderComponent),
                    MockDirective(TranslateDirective),
                    MockPipe(ArtemisTranslatePipe),
                    MockPipe(MathNodeLatexPipe),
                    MockPipe(KatexStringPipe),
                ],
            },
        });

        fixture = TestBed.createComponent(MathProblemParticipationComponent);
        component = fixture.componentInstance;
        mathSubmissionService = TestBed.inject(MathSubmissionService);
    });

    it('should initialize currentExpression from the start expression when no initial steps', () => {
        fixture.componentRef.setInput('problem', mockProblem());
        fixture.componentRef.setInput('exerciseId', 10);

        fixture.detectChanges();

        expect(component.currentExpression()).toBe(source);
        expect(component.steps().length).toBe(0);
    });

    it('should initialize currentExpression from the last saved step', () => {
        const step: DerivationStep = { stepIndex: 0, appliedRuleId: 'r', targetNodePath: [], resultExpression: other, direction: 'FORWARD' };
        fixture.componentRef.setInput('problem', mockProblem());
        fixture.componentRef.setInput('exerciseId', 10);
        fixture.componentRef.setInput('initialSteps', [step]);

        fixture.detectChanges();

        expect(component.currentExpression()).toBe(other);
        expect(component.steps().length).toBe(1);
    });

    it('should emit stepsChange when a manual step is confirmed', () => {
        fixture.componentRef.setInput('problem', mockProblem({ manualDerivation: true }));
        fixture.componentRef.setInput('exerciseId', 10);
        fixture.detectChanges();

        const emitted: DerivationStep[][] = [];
        component.stepsChange.subscribe((steps) => emitted.push(steps));

        component.selectedRuleId.set('r1');
        component.manualResultExpression.set(other);
        component.confirmManualStep();

        expect(component.steps().length).toBe(1);
        expect(emitted.length).toBe(1);
        expect(emitted[0].length).toBe(1);
    });

    it('should emit stepsChange when the last step is undone', () => {
        const step: DerivationStep = { stepIndex: 0, appliedRuleId: 'r', targetNodePath: [], resultExpression: other, direction: 'FORWARD' };
        fixture.componentRef.setInput('problem', mockProblem());
        fixture.componentRef.setInput('exerciseId', 10);
        fixture.componentRef.setInput('initialSteps', [step]);
        fixture.detectChanges();

        const emitted: DerivationStep[][] = [];
        component.stepsChange.subscribe((steps) => emitted.push(steps));

        component.undoLastStep();

        expect(component.steps().length).toBe(0);
        expect(emitted.length).toBe(1);
        expect(emitted[0].length).toBe(0);
        expect(component.currentExpression()).toBe(source);
    });

    it('should call getHints with (exerciseId, problemId, currentExpression)', () => {
        const getHintsSpy = vi.spyOn(mathSubmissionService, 'getHints').mockReturnValue(of([]));
        fixture.componentRef.setInput('problem', mockProblem());
        fixture.componentRef.setInput('exerciseId', 10);
        fixture.detectChanges();

        component.requestHints();

        expect(getHintsSpy).toHaveBeenCalledWith(10, 1, source);
    });
});
