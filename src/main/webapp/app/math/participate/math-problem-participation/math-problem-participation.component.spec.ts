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
import { BlockDefinitionModel, RewriteRuleModel } from 'app/math/shared/entities/block-definition.model';
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

    describe('per-problem rule subset', () => {
        const wild: MathNode = { type: 'wild', value: 'x' };
        const rule = (id: string): RewriteRuleModel => ({ id, name: id, paletteLatex: id, pattern: wild, template: wild, direction: 'FORWARD_ONLY' });

        const catalogue: BlockDefinitionModel = {
            type: 'mul',
            category: 'ARITHMETIC',
            label: 'Multiplication',
            paletteLatex: '\\cdot',
            slots: ['left', 'right'],
            rules: [rule('mul_comm'), rule('add_comm')],
            definitions: [rule('pow_zero'), rule('pow_succ')],
        };
        // The two blocks the induction workspace appends before handing this component its palette.
        const definitionsBlock: BlockDefinitionModel = {
            type: 'definitions',
            category: 'induction',
            label: 'Definitions',
            paletteLatex: '',
            slots: [],
            rules: [rule('pow_zero'), rule('pow_succ')],
        };
        const hypothesisBlock: BlockDefinitionModel = {
            type: 'hypothesis',
            category: 'induction',
            label: 'Hypothesis',
            paletteLatex: '',
            slots: [],
            rules: [rule('induction_hypothesis')],
        };

        const paletteRuleIds = (): string[] => component.filteredBlocks().flatMap((b) => (b.rules ?? []).map((r) => r.id));

        const setup = (allowedRuleIds: string[] | undefined, blocks: BlockDefinitionModel[] = [catalogue]) => {
            fixture.componentRef.setInput('problem', mockProblem({ allowedRuleIds }));
            fixture.componentRef.setInput('exerciseId', 10);
            fixture.componentRef.setInput('blocks', blocks);
            fixture.detectChanges();
        };

        it('should show every rule when the problem is unrestricted', () => {
            setup(undefined);
            expect(paletteRuleIds()).toEqual(['mul_comm', 'add_comm']);
        });

        it('should treat an empty subset as unrestricted', () => {
            setup([]);
            expect(paletteRuleIds()).toEqual(['mul_comm', 'add_comm']);
        });

        it('should hide a rule the instructor switched off', () => {
            setup(['mul_comm']);
            expect(paletteRuleIds()).toEqual(['mul_comm']);
        });

        it('should keep the definitions and the induction hypothesis under a restrictive subset', () => {
            // The induction workspace reuses this component, handing it the definitions and the IH inside `block.rules`.
            // Both are outside the subset's reach server-side, so hiding them would break every induction submission.
            setup(['mul_comm'], [catalogue, definitionsBlock, hypothesisBlock]);
            expect(paletteRuleIds()).toEqual(['mul_comm', 'pow_zero', 'pow_succ', 'induction_hypothesis']);
        });

        it('should compose with the applicable-rule filter, subset first', () => {
            const a: MathNode = { type: 'variable', value: 'a' };
            const b: MathNode = { type: 'variable', value: 'b' };
            const wildX: MathNode = { type: 'wild', value: 'x' };
            const wildY: MathNode = { type: 'wild', value: 'y' };
            const binary = (type: string, left: MathNode, right: MathNode): MathNode => ({ type, slots: { left: [left], right: [right] } });
            const commBlock: BlockDefinitionModel = {
                type: 'mul',
                category: 'ARITHMETIC',
                label: 'Multiplication',
                paletteLatex: '\\cdot',
                slots: ['left', 'right'],
                rules: [
                    { id: 'mul_comm', name: 'mul_comm', paletteLatex: '', pattern: binary('mul', wildX, wildY), template: binary('mul', wildY, wildX), direction: 'FORWARD_ONLY' },
                    { id: 'add_comm', name: 'add_comm', paletteLatex: '', pattern: binary('add', wildX, wildY), template: binary('add', wildY, wildX), direction: 'FORWARD_ONLY' },
                ],
            };
            // On a · b only mul_comm applies at the root; add_comm is applicable nowhere here.
            fixture.componentRef.setInput('problem', mockProblem({ onlyShowApplicableRules: true, allowedRuleIds: ['mul_comm'], sourceExpression: binary('mul', a, b) }));
            fixture.componentRef.setInput('exerciseId', 10);
            fixture.componentRef.setInput('blocks', [commBlock]);
            fixture.detectChanges();
            component.selectedNodePath.set([]);

            expect(paletteRuleIds()).toEqual(['mul_comm']);

            // Allowing only the rule that does not apply leaves nothing: both filters ran, and neither subsumes the other.
            fixture.componentRef.setInput('problem', mockProblem({ onlyShowApplicableRules: true, allowedRuleIds: ['add_comm'], sourceExpression: binary('mul', a, b) }));
            fixture.detectChanges();
            expect(paletteRuleIds()).toEqual([]);
        });
    });

    describe('no-regress filtering of applicable rules', () => {
        const a: MathNode = { type: 'variable', value: 'a' };
        const b: MathNode = { type: 'variable', value: 'b' };
        const c: MathNode = { type: 'variable', value: 'c' };
        const z: MathNode = { type: 'variable', value: 'z' };
        const wildX: MathNode = { type: 'wild', value: 'x' };
        const wildY: MathNode = { type: 'wild', value: 'y' };
        const binary = (type: string, left: MathNode, right: MathNode): MathNode => ({ type, slots: { left: [left], right: [right] } });

        const mulComm: RewriteRuleModel = {
            id: 'mul_comm',
            name: 'Commutativity of multiplication',
            paletteLatex: 'a \\cdot b = b \\cdot a',
            pattern: binary('mul', wildX, wildY),
            template: binary('mul', wildY, wildX),
            direction: 'BIDIRECTIONAL',
        };
        const addComm: RewriteRuleModel = {
            id: 'add_comm',
            name: 'Commutativity of addition',
            paletteLatex: 'a + b = b + a',
            pattern: binary('add', wildX, wildY),
            template: binary('add', wildY, wildX),
            direction: 'BIDIRECTIONAL',
        };
        const rulesBlock: BlockDefinitionModel = {
            type: 'mul',
            category: 'ARITHMETIC',
            label: 'Multiplication',
            paletteLatex: '\\cdot',
            slots: ['left', 'right'],
            rules: [mulComm, addComm],
        };

        it('should keep a purely-AC rule applicable when AC normalisation is enabled', () => {
            // (b + c) · a — mul_comm is the only way to reach a · (b + c) so distributivity can match.
            fixture.componentRef.setInput('problem', mockProblem({ acNormalization: true, sourceExpression: binary('mul', binary('add', b, c), a), targetExpression: z }));
            fixture.componentRef.setInput('exerciseId', 10);
            fixture.componentRef.setInput('blocks', [rulesBlock]);
            fixture.detectChanges();

            component.selectedNodePath.set([]);

            expect(component.applicableRuleIds().has('mul_comm')).toBe(true);
        });

        it('should let a purely-AC rule be applied when AC normalisation is enabled', () => {
            fixture.componentRef.setInput('problem', mockProblem({ acNormalization: true, sourceExpression: binary('mul', binary('add', b, c), a), targetExpression: z }));
            fixture.componentRef.setInput('exerciseId', 10);
            fixture.componentRef.setInput('blocks', [rulesBlock]);
            fixture.detectChanges();

            component.selectedNodePath.set([]);
            component.selectedRuleId.set('mul_comm');
            component.applySelectedRule();

            expect(component.ruleApplicationError()).toBeUndefined();
            expect(component.steps().length).toBe(1);
            expect(component.currentExpression()).toEqual(binary('mul', a, binary('add', b, c)));
        });

        it('should still exclude a rule that returns to an already visited state', () => {
            // a + b --add_comm--> b + a; applying add_comm again syntactically re-creates a + b.
            const step: DerivationStep = { stepIndex: 0, appliedRuleId: 'add_comm', targetNodePath: [], resultExpression: binary('add', b, a), direction: 'FORWARD' };
            fixture.componentRef.setInput('problem', mockProblem({ acNormalization: true, sourceExpression: binary('add', a, b), targetExpression: z }));
            fixture.componentRef.setInput('exerciseId', 10);
            fixture.componentRef.setInput('blocks', [rulesBlock]);
            fixture.componentRef.setInput('initialSteps', [step]);
            fixture.detectChanges();

            component.selectedNodePath.set([]);

            expect(component.applicableRuleIds().has('add_comm')).toBe(false);
        });

        it('should reject applying a rule that returns to an already visited state', () => {
            const step: DerivationStep = { stepIndex: 0, appliedRuleId: 'add_comm', targetNodePath: [], resultExpression: binary('add', b, a), direction: 'FORWARD' };
            fixture.componentRef.setInput('problem', mockProblem({ acNormalization: true, sourceExpression: binary('add', a, b), targetExpression: z }));
            fixture.componentRef.setInput('exerciseId', 10);
            fixture.componentRef.setInput('blocks', [rulesBlock]);
            fixture.componentRef.setInput('initialSteps', [step]);
            fixture.detectChanges();

            component.selectedNodePath.set([]);
            component.selectedRuleId.set('add_comm');
            component.applySelectedRule();

            expect(component.ruleApplicationError()).toBe('This step would return to a previously visited state.');
            expect(component.steps().length).toBe(1);
        });
    });
});
