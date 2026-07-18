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

    it('tags base/step steps by role and marks an IH application as a kind-B step carrying the applied instance', () => {
        const emitted: DerivationStep[][] = [];
        component.stepsChange.subscribe((steps) => emitted.push(steps));

        const one: MathNode = { type: 'number', value: '1' };
        const powN: MathNode = { type: 'pow', slots: { base: [one], exponent: [{ type: 'variable', value: 'n' }] } };
        // After pow_succ on the step goal 1^(S n) = 1 the student is at 1·1^n = 1; then applies the IH (1^n = 1)
        // at the `1^n` subterm (flatChildren path [0,1]), reaching 1·1 = 1.
        const afterPowSucc: MathNode = { type: 'eq', slots: { left: [{ type: 'mul', slots: { left: [one], right: [powN] } }], right: [one] } };
        const afterIh: MathNode = { type: 'eq', slots: { left: [{ type: 'mul', slots: { left: [one], right: [one] } }], right: [one] } };

        component.onBaseSteps([{ stepIndex: 0, appliedRuleId: 'pow_zero', resultExpression: one } as DerivationStep]);
        component.onStepSteps([
            { stepIndex: 0, appliedRuleId: 'pow_succ', resultExpression: afterPowSucc } as DerivationStep,
            { stepIndex: 1, appliedRuleId: 'induction_hypothesis', targetNodePath: [0, 1], resultExpression: afterIh } as DerivationStep,
        ]);

        const combined = emitted[emitted.length - 1];
        expect(combined).toHaveLength(3);

        const baseSteps = combined.filter((s) => s.derivationRole === 'BASE');
        expect(baseSteps).toHaveLength(1);
        expect(baseSteps[0].kind).toBe('A');

        const ihStep = combined.find((s) => s.appliedRuleId === 'induction_hypothesis');
        expect(ihStep?.derivationRole).toBe('STEP');
        expect(ihStep?.kind).toBe('B');
        // The carried equality is the concrete instance the student used — (subterm before) = (subterm after) at
        // the rewrite site — i.e. 1^n = 1, not the generic P(n) goal (Regate capabilities C2/D2).
        expect(ihStep?.substitutionEquation).toEqual({ type: 'eq', slots: { left: [powN], right: [one] } });
    });

    it('renders datatype-aware base/step constructor labels (ℕ by default)', () => {
        // Default problem is ℕ induction on n.
        expect(component.baseTermLabel()).toBe('0');
        expect(component.stepTermLabel()).toBe('S n');
    });

    it('renders list and tree constructor labels from the datatype schema (A-M5)', () => {
        const listProblem = new MathProblem();
        listProblem.goalMode = 'INDUCTION';
        listProblem.inductionVariable = 'l';
        listProblem.inductionDatatype = 'LIST';
        fixture.componentRef.setInput('problem', listProblem);
        fixture.detectChanges();
        expect(component.baseTermLabel()).toBe('nil');
        expect(component.stepTermLabel()).toBe('cons h t');

        const treeProblem = new MathProblem();
        treeProblem.goalMode = 'INDUCTION';
        treeProblem.inductionVariable = 't';
        treeProblem.inductionDatatype = 'TREE';
        fixture.componentRef.setInput('problem', treeProblem);
        fixture.detectChanges();
        expect(component.baseTermLabel()).toBe('empty');
        expect(component.stepTermLabel()).toBe('node l v r');
    });

    it('derives list base/step obligations and a P(t) hypothesis for LIST induction (A-M2)', () => {
        // sum(l, a) = a + summa(l), structural induction over the list l.
        const listProblem = new MathProblem();
        listProblem.goalMode = 'INDUCTION';
        listProblem.inductionVariable = 'l';
        listProblem.inductionDatatype = 'LIST';
        listProblem.points = 10;
        const sum = (...args: MathNode[]): MathNode => ({ type: 'apply', value: 'sum', slots: { args } });
        listProblem.goalExpression = {
            type: 'eq',
            slots: {
                left: [sum({ type: 'variable', value: 'l' }, { type: 'variable', value: 'a' })],
                right: [
                    {
                        type: 'add',
                        slots: { left: [{ type: 'variable', value: 'a' }], right: [{ type: 'apply', value: 'summa', slots: { args: [{ type: 'variable', value: 'l' }] } }] },
                    },
                ],
            },
        };
        fixture.componentRef.setInput('problem', listProblem);
        fixture.detectChanges();

        // Base case substitutes l -> nil (an apply node), step substitutes l -> cons(h, t).
        expect(component.baseProblem().goalExpression!.slots!.left[0].slots!.args[0]).toEqual({ type: 'apply', value: 'nil', slots: { args: [] } });
        expect(component.stepProblem().goalExpression!.slots!.left[0].slots!.args[0]).toEqual({
            type: 'apply',
            value: 'cons',
            slots: {
                args: [
                    { type: 'variable', value: 'h' },
                    { type: 'variable', value: 't' },
                ],
            },
        });

        // The single IH is P(t): the goal with l -> t, accumulator `a` wildcarded, the recursive field `t` literal.
        const ihRule = component
            .stepBlocks()
            .flatMap((block) => block.rules ?? [])
            .find((rule) => rule.id === 'induction_hypothesis');
        expect(ihRule).toBeDefined();
        expect(ihRule!.pattern).toEqual({
            type: 'apply',
            value: 'sum',
            slots: {
                args: [
                    { type: 'variable', value: 't' },
                    { type: 'wild', value: 'a' },
                ],
            },
        });
    });

    it('derives tree base/step obligations and two hypotheses P(l), P(r) for TREE induction (A-M3)', () => {
        // aux(t, a) = a + nodes(t), structural induction over the binary tree t (empty/node, two recursive fields).
        const treeProblem = new MathProblem();
        treeProblem.goalMode = 'INDUCTION';
        treeProblem.inductionVariable = 't';
        treeProblem.inductionDatatype = 'TREE';
        treeProblem.points = 10;
        const aux = (...args: MathNode[]): MathNode => ({ type: 'apply', value: 'aux', slots: { args } });
        treeProblem.goalExpression = {
            type: 'eq',
            slots: {
                left: [aux({ type: 'variable', value: 't' }, { type: 'variable', value: 'a' })],
                right: [
                    {
                        type: 'add',
                        slots: { left: [{ type: 'variable', value: 'a' }], right: [{ type: 'apply', value: 'nodes', slots: { args: [{ type: 'variable', value: 't' }] } }] },
                    },
                ],
            },
        };
        fixture.componentRef.setInput('problem', treeProblem);
        fixture.detectChanges();

        // Base substitutes t -> empty; step substitutes t -> node(l, v, r).
        expect(component.baseProblem().goalExpression!.slots!.left[0].slots!.args[0]).toEqual({ type: 'apply', value: 'empty', slots: { args: [] } });
        const stepCtor = component.stepProblem().goalExpression!.slots!.left[0].slots!.args[0];
        expect(stepCtor.value).toBe('node');
        expect(stepCtor.slots!.args.map((n) => n.value)).toEqual(['l', 'v', 'r']);

        // Two hypotheses, one per recursive field: P(l) and P(r), each with its own rule id and the field var literal.
        const ihRules = component
            .stepBlocks()
            .flatMap((block) => block.rules ?? [])
            .filter((rule) => rule.id.startsWith('induction_hypothesis'));
        expect(ihRules.map((r) => r.id)).toEqual(['induction_hypothesis_l', 'induction_hypothesis_r']);
        expect(ihRules[0].pattern).toEqual({
            type: 'apply',
            value: 'aux',
            slots: {
                args: [
                    { type: 'variable', value: 'l' },
                    { type: 'wild', value: 'a' },
                ],
            },
        });
        expect(ihRules[1].pattern).toEqual({
            type: 'apply',
            value: 'aux',
            slots: {
                args: [
                    { type: 'variable', value: 'r' },
                    { type: 'wild', value: 'a' },
                ],
            },
        });
    });

    it('builds the step-workspace IH as a schema over accumulator parameters (C2): non-induction variables become wildcards, the induction variable stays literal', () => {
        // Goal with an accumulator: P(x, n) = fact_aux(x, n) = x · fact(n), induction on n.
        const accProblem = new MathProblem();
        accProblem.goalMode = 'INDUCTION';
        accProblem.inductionVariable = 'n';
        accProblem.points = 10;
        accProblem.goalExpression = {
            type: 'eq',
            slots: {
                left: [
                    {
                        type: 'apply',
                        value: 'fact_aux',
                        slots: {
                            args: [
                                { type: 'variable', value: 'x' },
                                { type: 'variable', value: 'n' },
                            ],
                        },
                    },
                ],
                right: [
                    {
                        type: 'mul',
                        slots: { left: [{ type: 'variable', value: 'x' }], right: [{ type: 'apply', value: 'fact', slots: { args: [{ type: 'variable', value: 'n' }] } }] },
                    },
                ],
            },
        };
        fixture.componentRef.setInput('problem', accProblem);
        fixture.detectChanges();

        const ihRule = component
            .stepBlocks()
            .flatMap((block) => block.rules ?? [])
            .find((rule) => rule.id === 'induction_hypothesis');
        expect(ihRule).toBeDefined();
        // Accumulator x is wildcarded so the IH applies at a shifted accumulator; induction var n stays literal so
        // the schema still matches P(n) and never P(S n); the `apply` function names are untouched.
        expect(ihRule!.pattern).toEqual({
            type: 'apply',
            value: 'fact_aux',
            slots: {
                args: [
                    { type: 'wild', value: 'x' },
                    { type: 'variable', value: 'n' },
                ],
            },
        });
        expect(ihRule!.template).toEqual({
            type: 'mul',
            slots: { left: [{ type: 'wild', value: 'x' }], right: [{ type: 'apply', value: 'fact', slots: { args: [{ type: 'variable', value: 'n' }] } }] },
        });
    });
});
