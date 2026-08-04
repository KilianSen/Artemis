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
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { MathNode } from 'app/math/shared/entities/math-node.model';
import { BlockDefinitionModel, RewriteRuleModel } from 'app/math/shared/entities/block-definition.model';
import { WritableSignal, signal } from '@angular/core';

describe('MathProblemEditComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathProblemEditComponent;
    let fixture: ComponentFixture<MathProblemEditComponent>;
    let mathExerciseService: { verifyReachability: ReturnType<typeof vi.fn> };
    let registryBlocks: WritableSignal<BlockDefinitionModel[]>;

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
        registryBlocks = signal<BlockDefinitionModel[]>([]);

        TestBed.configureTestingModule({
            imports: [MathProblemEditComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: MathExerciseService, useValue: mathExerciseService },
                {
                    provide: MathBlockRegistryService,
                    useValue: {
                        blocks: registryBlocks.asReadonly(),
                        getBlockRegistry: vi.fn().mockReturnValue(of([])),
                    },
                },
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

    it('offers the three induction datatypes (ℕ / list / tree)', () => {
        createComponent(new MathProblem());
        expect(component.inductionDatatypeOptions.map((option) => option.value)).toEqual(['NAT', 'LIST', 'TREE']);
    });

    it('onInductionDatatypeChange updates the datatype and resets the example derivation', () => {
        const problem = new MathProblem();
        problem.exampleDerivations = [{} as any];
        createComponent(problem);
        component.onInductionDatatypeChange('LIST');
        expect(problem.inductionDatatype).toBe('LIST');
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

    it('offers the curated starter templates', () => {
        createComponent(new MathProblem());
        const options = component.starterTemplateOptions();
        expect(options).toHaveLength(9);
        expect(options.map((o) => o.value)).toContain('induction-add-zero');
        expect(options.map((o) => o.value)).toContain('induction-fact-accumulator');
        expect(options.map((o) => o.value)).toContain('induction-list-sum');
        expect(options.map((o) => o.value)).toContain('induction-tree-count');
    });

    it('applyStarterTemplate pre-fills a transformation problem and emits it', () => {
        const problem = new MathProblem();
        problem.exampleDerivations = [{} as any];
        createComponent(problem);
        let emitted: MathProblem | undefined;
        component.problemChange.subscribe((p) => (emitted = p));

        component.applyStarterTemplate('left-identity');

        expect(problem.goalMode).toBe('TRANSFORMATION');
        expect(problem.graderTypes).toEqual(['PATH_CHECKER']);
        expect(problem.sourceExpression?.type).toBe('add');
        expect(problem.targetExpression).toEqual({ type: 'variable', value: 'x' });
        expect(problem.exampleDerivations).toEqual([]);
        expect(emitted).toBe(problem);
    });

    it('applyStarterTemplate configures an induction problem and clears source/target', () => {
        const problem = new MathProblem();
        problem.sourceExpression = { type: 'variable', value: 'y' } as MathNode;
        problem.targetExpression = { type: 'variable', value: 'y' } as MathNode;
        createComponent(problem);

        component.applyStarterTemplate('induction-add-zero');

        expect(problem.goalMode).toBe('INDUCTION');
        expect(problem.inductionVariable).toBe('n');
        expect(problem.graderTypes).toEqual(['CVC5REGATE']);
        expect(problem.goalExpression?.type).toBe('eq');
        expect(problem.sourceExpression).toBeUndefined();
        expect(problem.targetExpression).toBeUndefined();
    });

    it('applyStarterTemplate ignores an unknown template id', () => {
        const problem = new MathProblem();
        createComponent(problem);
        const before = problem.goalMode;
        component.applyStarterTemplate('does-not-exist');
        expect(problem.goalMode).toBe(before);
    });

    describe('allowed rule subset', () => {
        const rule = (id: string): RewriteRuleModel => ({ id, name: id, paletteLatex: id, pattern: {} as MathNode, template: {} as MathNode, direction: 'FORWARD_ONLY' });

        beforeEach(() => {
            registryBlocks.set([
                { type: 'add', category: 'arith', label: 'Addition', paletteLatex: '+', rules: [rule('add_zero'), rule('add_comm')], definitions: [rule('summa_nil')] },
                // Definitions only: RuleSubsetPolicy never restricts them, so the block contributes no option and drops out.
                { type: 'pow', category: 'arith', label: 'Power', paletteLatex: '^', definitions: [rule('pow_zero'), rule('pow_succ')] },
            ]);
        });

        it('offers registry rules grouped by block and excludes recursive definitions', () => {
            createComponent(new MathProblem());

            expect(component.ruleSubsetOptionGroups()).toEqual([
                {
                    label: 'Addition',
                    rules: [
                        { value: 'add_zero', label: 'add_zero' },
                        { value: 'add_comm', label: 'add_comm' },
                    ],
                },
            ]);
        });

        it('treats an unset and an empty subset as unrestricted', () => {
            const problem = new MathProblem();
            createComponent(problem);

            expect(problem.allowedRuleIds).toBeUndefined();
            expect(component.ruleSubsetUnrestricted()).toBe(true);

            problem.allowedRuleIds = [];
            expect(component.ruleSubsetUnrestricted()).toBe(true);
        });

        it('writes the selected rule ids onto the problem and emits the change', () => {
            const problem = new MathProblem();
            createComponent(problem);
            let emitted: MathProblem | undefined;
            component.problemChange.subscribe((p) => (emitted = p));

            component.onAllowedRuleIdsChange(['add_zero', 'add_comm']);

            expect(problem.allowedRuleIds).toEqual(['add_zero', 'add_comm']);
            expect(component.ruleSubsetUnrestricted()).toBe(false);
            expect(emitted).toBe(problem);
        });

        it('clears back to undefined rather than an empty array when the selection is emptied', () => {
            const problem = new MathProblem();
            problem.allowedRuleIds = ['add_zero'];
            createComponent(problem);

            component.onAllowedRuleIdsChange([]);

            // Not [], which would round-trip as an authored-but-empty subset; undefined is the unrestricted state.
            expect(problem.allowedRuleIds).toBeUndefined();
            expect(component.ruleSubsetUnrestricted()).toBe(true);
        });

        it('treats a cleared control (undefined) the same as an empty selection', () => {
            const problem = new MathProblem();
            problem.allowedRuleIds = ['add_zero'];
            createComponent(problem);

            component.onAllowedRuleIdsChange(undefined);

            expect(problem.allowedRuleIds).toBeUndefined();
        });
    });
});
