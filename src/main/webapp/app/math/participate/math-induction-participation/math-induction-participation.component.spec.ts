import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { MathInductionParticipationComponent } from 'app/math/participate/math-induction-participation/math-induction-participation.component';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { MathNode } from 'app/math/shared/entities/math-node.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';

describe('MathInductionParticipationComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathInductionParticipationComponent;
    let fixture: ReturnType<typeof TestBed.createComponent<MathInductionParticipationComponent>>;

    // Goal P(n): 1^n = 1
    const goal: MathNode = {
        type: 'eq',
        slots: {
            left: [{ type: 'pow', slots: { base: [{ type: 'number', value: '1' }], exponent: [{ type: 'variable', value: 'n' }] } }],
            right: [{ type: 'number', value: '1' }],
        },
    };

    function makeProblem(): MathProblem {
        const problem = new MathProblem();
        problem.goalMode = 'INDUCTION';
        problem.goalExpression = goal;
        problem.inductionVariable = 'n';
        problem.points = 10;
        return problem;
    }

    beforeEach(() => {
        TestBed.configureTestingModule({ imports: [MathInductionParticipationComponent] }).overrideComponent(MathInductionParticipationComponent, {
            set: { imports: [], template: '' },
        });
        fixture = TestBed.createComponent(MathInductionParticipationComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('problem', makeProblem());
        fixture.componentRef.setInput('exerciseId', 1);
        fixture.componentRef.setInput('blocks', []);
        fixture.detectChanges();
    });

    it('exposes the induction variable from the problem (defaulting to n)', () => {
        expect(component.inductionVar()).toBe('n');
    });

    it('derives the base case P(0) by substituting the induction variable with 0, reframed as an EQUATION', () => {
        const base = component.baseProblem();
        expect(base.goalMode).toBe('EQUATION');
        // 1^n = 1  ->  1^0 = 1
        expect(base.goalExpression!.slots!.left[0].slots!.exponent[0]).toEqual({ type: 'number', value: '0' });
    });

    it('derives the inductive step P(S n) by substituting the induction variable with succ(n)', () => {
        const step = component.stepProblem();
        expect(step.goalMode).toBe('EQUATION');
        expect(step.goalExpression!.slots!.left[0].slots!.exponent[0]).toEqual({ type: 'succ', slots: { inner: [{ type: 'variable', value: 'n' }] } });
    });

    it('offers the induction hypothesis as an applicable rule in the step workspace only', () => {
        const stepHasIh = component.stepBlocks().some((block) => (block.rules ?? []).some((rule) => rule.id === 'induction_hypothesis'));
        const baseHasIh = component.baseBlocks().some((block) => (block.rules ?? []).some((rule) => rule.id === 'induction_hypothesis'));
        expect(stepHasIh).toBe(true);
        expect(baseHasIh).toBe(false);
    });

    it('tags base/step steps by role and marks an IH application as a kind-B step carrying the equality', () => {
        const emitted: DerivationStep[][] = [];
        component.stepsChange.subscribe((steps) => emitted.push(steps));

        component.onBaseSteps([{ stepIndex: 0, appliedRuleId: 'pow_zero', resultExpression: { type: 'number', value: '1' } } as DerivationStep]);
        component.onStepSteps([
            { stepIndex: 0, appliedRuleId: 'pow_succ', resultExpression: goal } as DerivationStep,
            { stepIndex: 1, appliedRuleId: 'induction_hypothesis', resultExpression: { type: 'number', value: '1' } } as DerivationStep,
        ]);

        const combined = emitted[emitted.length - 1];
        expect(combined).toHaveLength(3);

        const baseSteps = combined.filter((s) => s.derivationRole === 'BASE');
        expect(baseSteps).toHaveLength(1);
        expect(baseSteps[0].kind).toBe('A');

        const ihStep = combined.find((s) => s.appliedRuleId === 'induction_hypothesis');
        expect(ihStep?.derivationRole).toBe('STEP');
        expect(ihStep?.kind).toBe('B');
        expect(ihStep?.substitutionEquation).toEqual(goal);
    });
});
