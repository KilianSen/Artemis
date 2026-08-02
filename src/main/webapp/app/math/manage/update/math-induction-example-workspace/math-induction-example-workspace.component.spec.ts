import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { MathInductionExampleWorkspaceComponent } from 'app/math/manage/update/math-induction-example-workspace/math-induction-example-workspace.component';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { MathNode } from 'app/math/shared/entities/math-node.model';
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

    function create(problem: MathProblem, initialSteps: DerivationStep[] = []) {
        // Re-created per case: `initialSteps` seeds constructor state, so a test that varies it needs a fresh module.
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({ imports: [MathInductionExampleWorkspaceComponent] }).overrideComponent(MathInductionExampleWorkspaceComponent, {
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

    it('should offer the induction hypothesis as a bidirectional palette rule', () => {
        const blocks = component.hypothesisBlocks();
        expect(blocks).toHaveLength(1);
        expect(blocks[0].rules).toHaveLength(1);
        expect(blocks[0].rules![0].id).toBe('induction_hypothesis');
        // Bidirectional so a proof may fold the RHS back into the recursive call, which the grader now accepts.
        expect(blocks[0].rules![0].direction).toBe('BIDIRECTIONAL');
    });

    it('should offer one hypothesis per recursive field for a tree', () => {
        create(makeProblem('TREE'));
        const rules = component.hypothesisBlocks()[0].rules!;
        expect(rules.map((r) => r.id)).toEqual(['induction_hypothesis_l', 'induction_hypothesis_r']);
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
