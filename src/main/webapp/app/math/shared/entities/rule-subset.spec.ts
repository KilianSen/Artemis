import { describe, expect, it } from 'vitest';
import { BlockDefinitionModel, RewriteRuleModel } from 'app/math/shared/entities/block-definition.model';
import { filterBlocksByRuleSubset } from 'app/math/shared/entities/rule-subset';
import { MathNode } from 'app/math/shared/entities/math-node.model';

describe('filterBlocksByRuleSubset', () => {
    const wild: MathNode = { type: 'wild', value: 'x' };
    const rule = (id: string): RewriteRuleModel => ({ id, name: id, paletteLatex: id, pattern: wild, template: wild, direction: 'FORWARD_ONLY' });

    const catalogue: BlockDefinitionModel = {
        type: 'mul',
        category: 'ARITHMETIC',
        label: 'Multiplication',
        paletteLatex: '\\cdot',
        slots: ['left', 'right'],
        rules: [rule('mul_comm'), rule('add_comm')],
        // The recursive definitions ride along on the catalogue blocks, as they do on the wire.
        definitions: [rule('pow_zero'), rule('pow_succ')],
    };
    /** How the induction workspace surfaces the definitions: as rules of a synthetic palette block. */
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
        rules: [rule('induction_hypothesis_l'), rule('induction_hypothesis_r')],
    };

    const ruleIdsOf = (blocks: BlockDefinitionModel[]): string[] => blocks.flatMap((b) => (b.rules ?? []).map((r) => r.id));

    it('returns the palette untouched when the subset is undefined or empty', () => {
        const blocks = [catalogue, definitionsBlock];
        expect(filterBlocksByRuleSubset(blocks, undefined)).toBe(blocks);
        expect(filterBlocksByRuleSubset(blocks, [])).toBe(blocks);
    });

    it('keeps only the subset among the catalogue rules', () => {
        expect(ruleIdsOf(filterBlocksByRuleSubset([catalogue], ['mul_comm']))).toEqual(['mul_comm']);
    });

    it('drops a block left with no rule', () => {
        expect(filterBlocksByRuleSubset([catalogue], ['not_in_this_block'])).toEqual([]);
    });

    it('never hides the recursive definitions, even under a subset naming none of them', () => {
        const filtered = filterBlocksByRuleSubset([catalogue, definitionsBlock], ['mul_comm']);
        expect(ruleIdsOf(filtered)).toEqual(['mul_comm', 'pow_zero', 'pow_succ']);
        // …and the definitions block survives even when every catalogue block is emptied out.
        expect(ruleIdsOf(filterBlocksByRuleSubset([catalogue, definitionsBlock], ['nothing_here']))).toEqual(['pow_zero', 'pow_succ']);
    });

    it('never hides the synthetic induction hypotheses, which exist in no registry and can be in no subset', () => {
        const filtered = filterBlocksByRuleSubset([catalogue, definitionsBlock, hypothesisBlock], ['mul_comm']);
        expect(ruleIdsOf(filtered)).toEqual(['mul_comm', 'pow_zero', 'pow_succ', 'induction_hypothesis_l', 'induction_hypothesis_r']);
    });

    it('does not mutate the blocks it narrows', () => {
        filterBlocksByRuleSubset([catalogue], ['mul_comm']);
        expect(ruleIdsOf([catalogue])).toEqual(['mul_comm', 'add_comm']);
    });
});
