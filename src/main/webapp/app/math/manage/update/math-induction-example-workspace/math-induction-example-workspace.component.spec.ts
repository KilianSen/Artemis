import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MathInductionExampleWorkspaceComponent } from 'app/math/manage/update/math-induction-example-workspace/math-induction-example-workspace.component';
import { MathDerivationWorkspaceComponent } from 'app/math/manage/update/math-derivation-workspace/math-derivation-workspace.component';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { MathNode } from 'app/math/shared/entities/math-node.model';
import { BlockDefinitionModel, RewriteRuleModel } from 'app/math/shared/entities/block-definition.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';

describe('MathInductionExampleWorkspaceComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathInductionExampleWorkspaceComponent;
    let fixture: ReturnType<typeof TestBed.createComponent<MathInductionExampleWorkspaceComponent>>;

    // Goal P(n): 1^n = 1
    const goal: MathNode = {
        type: 'eq',
        slots: {
            left: [{ type: 'pow', slots: { base: [{ type: 'number', value: '1' }], exponent: [{ type: 'variable', value: 'n' }] } }],
            right: [{ type: 'number', value: '1' }],
        },
    };

    function makeProblem(datatype: 'NAT' | 'LIST' | 'TREE' = 'NAT'): MathProblem {
        const problem = new MathProblem();
        problem.goalMode = 'INDUCTION';
        problem.goalExpression = goal;
        problem.inductionVariable = 'n';
        problem.inductionDatatype = datatype;
        return problem;
    }

    const wild: MathNode = { type: 'wild', value: 'x' };
    const rule = (id: string): RewriteRuleModel => ({ id, name: id, paletteLatex: id, pattern: wild, template: wild, direction: 'FORWARD_ONLY' });

    /** A catalogue block as the block registry serves it: citable rules plus the code-contributed recursive definitions. */
    const catalogue: BlockDefinitionModel = {
        type: 'pow',
        category: 'ARITHMETIC',
        label: 'Power',
        paletteLatex: '^',
        slots: ['base', 'exponent'],
        rules: [rule('mul_comm'), rule('add_comm')],
        definitions: [rule('pow_zero'), rule('pow_succ')],
    };

    function create(problem: MathProblem, initialSteps: DerivationStep[] = [], registry: BlockDefinitionModel[] = []) {
        // Re-created per case: `initialSteps` seeds constructor state, so a test that varies it needs a fresh module.
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            imports: [MathInductionExampleWorkspaceComponent],
            providers: [MockProvider(MathBlockRegistryService, { getBlockRegistry: () => of(registry) as any, descriptorFor: () => undefined })],
        }).overrideComponent(MathInductionExampleWorkspaceComponent, {
            set: { imports: [], template: '' },
        });
        fixture = TestBed.createComponent(MathInductionExampleWorkspaceComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('problem', problem);
        fixture.componentRef.setInput('initialSteps', initialSteps);
        fixture.detectChanges();
    }

    beforeEach(() => create(makeProblem()));

    it('should instantiate the goal at the base and step constructors', () => {
        // P(0): the induction variable is replaced by the base term.
        expect(component.baseGoal()).toEqual({
            type: 'eq',
            slots: {
                left: [{ type: 'pow', slots: { base: [{ type: 'number', value: '1' }], exponent: [{ type: 'number', value: '0' }] } }],
                right: [{ type: 'number', value: '1' }],
            },
        });
        // P(S n): and by the step constructor.
        expect(component.stepGoal()?.slots?.['left']?.[0]?.slots?.['exponent']?.[0]?.type).toBe('succ');
    });

    it('should label the cases by their constructors', () => {
        expect(component.baseTermLabel()).toBe('0');
        expect(component.stepTermLabel()).toBe('S n');
    });

    /** The rules of one of the extra palette blocks the component hands to a case workspace. */
    const extraRuleIds = (blocks: BlockDefinitionModel[], type: string): string[] => (blocks.find((b) => b.type === type)?.rules ?? []).map((r) => r.id);

    it('should offer the induction hypothesis as a bidirectional palette rule', () => {
        const hypothesis = component.stepExtraBlocks().find((b) => b.type === 'hypothesis')!;
        expect(hypothesis.rules).toHaveLength(1);
        expect(hypothesis.rules![0].id).toBe('induction_hypothesis');
        // Bidirectional so a proof may fold the RHS back into the recursive call, which the grader now accepts.
        expect(hypothesis.rules![0].direction).toBe('BIDIRECTIONAL');
    });

    it('should offer one hypothesis per recursive field for a tree', () => {
        create(makeProblem('TREE'));
        expect(extraRuleIds(component.stepExtraBlocks(), 'hypothesis')).toEqual(['induction_hypothesis_l', 'induction_hypothesis_r']);
    });

    it('should add the recursive definitions to both cases and the hypotheses to the step case only', () => {
        // Without the definitions the instructor cannot author the very derivation their students must produce.
        create(makeProblem(), [], [catalogue]);

        expect(component.baseExtraBlocks().map((b) => b.type)).toEqual(['definitions']);
        expect(extraRuleIds(component.baseExtraBlocks(), 'definitions')).toEqual(['pow_zero', 'pow_succ']);
        expect(component.stepExtraBlocks().map((b) => b.type)).toEqual(['definitions', 'hypothesis']);
        expect(extraRuleIds(component.stepExtraBlocks(), 'definitions')).toEqual(['pow_zero', 'pow_succ']);
    });

    it('should add no definitions block when no block contributes a definition', () => {
        create(makeProblem(), [], [{ type: 'mul', category: 'ARITHMETIC', label: 'Multiplication', paletteLatex: '\\cdot', slots: [], rules: [rule('mul_comm')] }]);

        expect(component.baseExtraBlocks()).toEqual([]);
        expect(component.stepExtraBlocks().map((b) => b.type)).toEqual(['hypothesis']);
    });

    it('should tag each case with its derivation role and concatenate base before step', () => {
        const emitted: DerivationStep[][] = [];
        component.stepsChange.subscribe((steps) => emitted.push(steps));

        component.onBaseSteps([{ stepIndex: 0, appliedRuleId: 'pow_zero', targetNodePath: [0], resultExpression: goal } as DerivationStep]);
        component.onStepSteps([{ stepIndex: 0, appliedRuleId: 'pow_succ', targetNodePath: [0], resultExpression: goal } as DerivationStep]);

        const last = emitted[emitted.length - 1];
        expect(last.map((s) => s.derivationRole)).toEqual(['BASE', 'STEP']);
        expect(last.every((s) => s.kind === 'A')).toBe(true);
    });

    it('should mark a hypothesis step kind-B and record the concrete instance it applied', () => {
        const emitted: DerivationStep[][] = [];
        component.stepsChange.subscribe((steps) => emitted.push(steps));

        // Applying the IH at the whole right-hand side of P(S n).
        component.onStepSteps([{ stepIndex: 0, appliedRuleId: 'induction_hypothesis', targetNodePath: [1], resultExpression: goal } as DerivationStep]);

        const step = emitted[emitted.length - 1][0];
        expect(step.kind).toBe('B');
        expect(step.derivationRole).toBe('STEP');
        // The equation is the (before, after) pair at the rewrite site, not the generic P(n).
        expect(step.substitutionEquation?.type).toBe('eq');
    });

    it('should bind the rule subset onto both rendered case workspaces', () => {
        // Renders the real template (only the two workspaces are stubbed) so the binding itself is under test.
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            imports: [MathInductionExampleWorkspaceComponent],
            providers: [MockProvider(MathBlockRegistryService, { getBlockRegistry: () => of([]) as any, descriptorFor: () => undefined })],
        }).overrideComponent(MathDerivationWorkspaceComponent, { set: { imports: [], template: '' } });
        const rendered = TestBed.createComponent(MathInductionExampleWorkspaceComponent);
        const problem = makeProblem();
        problem.allowedRuleIds = ['mul_comm'];
        rendered.componentRef.setInput('problem', problem);
        rendered.detectChanges();

        const workspaces = rendered.debugElement.queryAll(By.directive(MathDerivationWorkspaceComponent));
        expect(workspaces).toHaveLength(2);
        expect(workspaces.map((w) => w.componentInstance.allowedRuleIds())).toEqual([['mul_comm'], ['mul_comm']]);
    });

    it('should hand the problem rule subset to both case workspaces, undefined while unrestricted', () => {
        expect(component.allowedRuleIds()).toBeUndefined();

        const restricted = makeProblem();
        restricted.allowedRuleIds = ['mul_comm'];
        create(restricted);

        // Narrows both palettes; the hypotheses stay offered because they are exempt from the narrowing.
        expect(component.allowedRuleIds()).toEqual(['mul_comm']);
        expect(extraRuleIds(component.stepExtraBlocks(), 'hypothesis')).toEqual(['induction_hypothesis']);
    });

    /**
     * The palette the instructor actually gets, asserted on the rendered case workspaces: the catalogue each fetches
     * for itself, plus this component's extra blocks, narrowed by the problem's subset. Mirrors the student-side
     * assertions in {@code MathInductionParticipationComponent}'s spec, which is the bar this has to meet.
     */
    describe('rendered case palettes', () => {
        function render(problem: MathProblem) {
            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                imports: [MathInductionExampleWorkspaceComponent],
                providers: [MockProvider(MathBlockRegistryService, { getBlockRegistry: () => of([catalogue]) as any, descriptorFor: () => undefined })],
            }).overrideComponent(MathDerivationWorkspaceComponent, { set: { imports: [], template: '' } });
            const rendered = TestBed.createComponent(MathInductionExampleWorkspaceComponent);
            rendered.componentRef.setInput('problem', problem);
            rendered.detectChanges();
            return rendered.debugElement.queryAll(By.directive(MathDerivationWorkspaceComponent)).map((workspace) => paletteOf(workspace.componentInstance));
        }

        const paletteOf = (workspace: MathDerivationWorkspaceComponent): string[] => workspace.filteredBlocks().flatMap((b) => (b.rules ?? []).map((r) => r.id));

        it('offers the definitions in both cases and the hypothesis in the step case', () => {
            const [base, step] = render(makeProblem());
            expect(base).toEqual(['mul_comm', 'add_comm', 'pow_zero', 'pow_succ']);
            expect(step).toEqual(['mul_comm', 'add_comm', 'pow_zero', 'pow_succ', 'induction_hypothesis']);
        });

        it('narrows the catalogue to the rule subset while keeping the definitions and the hypothesis', () => {
            // Both are outside the subset's reach server-side (RuleSubsetPolicy), so the example editor keeps offering them —
            // and a step citing one is accepted by the save-time check (MathExerciseResource#validateAllowedRuleIds).
            const restricted = makeProblem();
            restricted.allowedRuleIds = ['mul_comm'];
            const [base, step] = render(restricted);
            expect(base).toEqual(['mul_comm', 'pow_zero', 'pow_succ']);
            expect(step).toEqual(['mul_comm', 'pow_zero', 'pow_succ', 'induction_hypothesis']);
        });
    });

    it('should split previously authored steps back into their cases', () => {
        const stored: DerivationStep[] = [
            { stepIndex: 0, appliedRuleId: 'pow_zero', targetNodePath: [0], resultExpression: goal, derivationRole: 'BASE' } as DerivationStep,
            { stepIndex: 0, appliedRuleId: 'pow_succ', targetNodePath: [0], resultExpression: goal, derivationRole: 'STEP' } as DerivationStep,
        ];
        create(makeProblem(), stored);
        expect(component.initialBaseSteps()).toHaveLength(1);
        expect(component.initialStepSteps()).toHaveLength(1);
        expect(component.initialBaseSteps()[0].appliedRuleId).toBe('pow_zero');
    });

    it('should keep the untouched case when only one is edited', () => {
        const stored: DerivationStep[] = [{ stepIndex: 0, appliedRuleId: 'pow_zero', targetNodePath: [0], resultExpression: goal, derivationRole: 'BASE' } as DerivationStep];
        create(makeProblem(), stored);

        const emitted: DerivationStep[][] = [];
        component.stepsChange.subscribe((steps) => emitted.push(steps));
        component.onStepSteps([{ stepIndex: 0, appliedRuleId: 'pow_succ', targetNodePath: [0], resultExpression: goal } as DerivationStep]);

        // Editing the step case must not drop the base case authored earlier.
        expect(emitted[emitted.length - 1].map((s) => s.derivationRole)).toEqual(['BASE', 'STEP']);
    });
});
