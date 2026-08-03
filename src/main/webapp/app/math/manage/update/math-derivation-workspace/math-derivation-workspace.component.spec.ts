import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MathDerivationWorkspaceComponent } from 'app/math/manage/update/math-derivation-workspace/math-derivation-workspace.component';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathNode } from 'app/math/shared/entities/math-node.model';
import { BlockDefinitionModel, RewriteRuleModel } from 'app/math/shared/entities/block-definition.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';

describe('MathDerivationWorkspaceComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathDerivationWorkspaceComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MathDerivationWorkspaceComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(MathBlockRegistryService, { getBlockRegistry: () => of([]) as any, descriptorFor: () => undefined }),
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
            ],
        }).overrideComponent(MathDerivationWorkspaceComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(MathDerivationWorkspaceComponent);
        component = fixture.componentInstance;
        const x: MathNode = { type: 'var', value: 'x' };
        fixture.componentRef.setInput('sourceExpression', x);
        fixture.componentRef.setInput('targetExpression', x);
        component.ngOnInit();
    });

    it('initialises the current expression from the source input', () => {
        expect(component.currentExpression()?.type).toBe('var');
    });

    it('rootNodes mirrors the current expression', () => {
        expect(component.rootNodes().length).toBe(1);
    });

    it('filteredBlocks returns an empty list when no blocks are loaded', () => {
        expect(component.filteredBlocks()).toEqual([]);
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

        /** Builds a second workspace instance with AC normalisation on and the commutativity rules in the palette. */
        const setup = (source: MathNode, steps: DerivationStep[] = []): MathDerivationWorkspaceComponent => {
            const acFixture: ComponentFixture<MathDerivationWorkspaceComponent> = TestBed.createComponent(MathDerivationWorkspaceComponent);
            acFixture.componentRef.setInput('sourceExpression', source);
            acFixture.componentRef.setInput('targetExpression', z);
            acFixture.componentRef.setInput('acNormalization', true);
            acFixture.componentRef.setInput('extraBlocks', [rulesBlock]);
            acFixture.componentRef.setInput('initialSteps', steps);
            acFixture.componentInstance.ngOnInit();
            return acFixture.componentInstance;
        };

        it('should keep a purely-AC rule applicable when AC normalisation is enabled', () => {
            // (b + c) · a — mul_comm is the only way to reach a · (b + c) so distributivity can match.
            const acComponent = setup(binary('mul', binary('add', b, c), a));

            acComponent.selectedNodePath.set([]);

            expect(acComponent.applicableRuleIds().has('mul_comm')).toBe(true);
        });

        it('should let a purely-AC rule be applied when AC normalisation is enabled', () => {
            const acComponent = setup(binary('mul', binary('add', b, c), a));

            acComponent.selectedNodePath.set([]);
            acComponent.selectedRuleId.set('mul_comm');
            acComponent.applySelectedRule();

            expect(acComponent.ruleApplicationError()).toBeUndefined();
            expect(acComponent.steps().length).toBe(1);
            expect(acComponent.currentExpression()).toEqual(binary('mul', a, binary('add', b, c)));
        });

        it('should still exclude a rule that returns to an already visited state', () => {
            // a + b --add_comm--> b + a; applying add_comm again syntactically re-creates a + b.
            const step: DerivationStep = { stepIndex: 0, appliedRuleId: 'add_comm', targetNodePath: [], resultExpression: binary('add', b, a), direction: 'FORWARD' };
            const acComponent = setup(binary('add', a, b), [step]);

            acComponent.selectedNodePath.set([]);

            expect(acComponent.applicableRuleIds().has('add_comm')).toBe(false);
        });

        it('should reject applying a rule that returns to an already visited state', () => {
            const step: DerivationStep = { stepIndex: 0, appliedRuleId: 'add_comm', targetNodePath: [], resultExpression: binary('add', b, a), direction: 'FORWARD' };
            const acComponent = setup(binary('add', a, b), [step]);

            acComponent.selectedNodePath.set([]);
            acComponent.selectedRuleId.set('add_comm');
            acComponent.applySelectedRule();

            expect(acComponent.ruleApplicationError()).toBe('This step would return to a previously visited state.');
            expect(acComponent.steps().length).toBe(1);
        });
    });
});
