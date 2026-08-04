import { BlockDefinitionModel, RewriteRuleModel } from 'app/math/shared/entities/block-definition.model';
import { IH_RULE_ID } from 'app/math/shared/entities/induction-schema';

/**
 * Narrows a rule palette to the problem's allowed rule subset ({@code MathProblem.allowedRuleIds}).
 * <p>
 * Cosmetic only, and deliberately so: the server re-checks every submitted step (see {@code RuleSubsetPolicy}) because
 * the wire carries a bare rule id anyone can set by hand. This just stops a palette from offering a rule the instructor
 * switched off. It is applied <em>before</em> the cursor-dependent applicable-rule filter, which is a different feature.
 * <p>
 * Mirrors the two exemptions {@code RuleSubsetPolicy} makes, because both are load-bearing for induction:
 * <ul>
 * <li>the recursive <b>definitions</b> ({@code pow_zero}, {@code fact_succ}, {@code summa_nil}, …), which are always
 * trusted and are what drives an induction proof — the induction workspace surfaces them as a synthetic palette block,
 * so unlike an ordinary palette they do appear in {@code block.rules} and would otherwise be hidden here;</li>
 * <li>the synthetic <b>induction hypotheses</b> ({@code induction_hypothesis}, {@code induction_hypothesis_<field>}),
 * which are minted by the client, exist in no registry and can therefore be in no subset.</li>
 * </ul>
 *
 * @param blocks         the palette blocks to narrow
 * @param allowedRuleIds the problem's subset; undefined or empty means unrestricted, and the palette is returned as is
 * @return the narrowed blocks, with blocks left holding no rule dropped
 */
export function filterBlocksByRuleSubset(blocks: BlockDefinitionModel[], allowedRuleIds: string[] | undefined): BlockDefinitionModel[] {
    if (!allowedRuleIds?.length) {
        return blocks;
    }
    const allowedIds = new Set(allowedRuleIds);
    // Derived from the palette itself, exactly as the server derives them from the block registry.
    const definitionIds = new Set(blocks.flatMap((b) => b.definitions ?? []).map((d) => d.id));
    const citable = (rule: RewriteRuleModel) => allowedIds.has(rule.id) || definitionIds.has(rule.id) || rule.id.startsWith(IH_RULE_ID);
    return blocks.map((block) => ({ ...block, rules: (block.rules ?? []).filter(citable) })).filter((block) => block.rules.length > 0);
}
