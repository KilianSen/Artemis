package de.tum.cit.aet.artemis.math.regate;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.math.domain.BlockDefinition;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.NotEqualToConstant;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;
import de.tum.cit.aet.artemis.math.domain.RuleConstraint;
import de.tum.cit.aet.artemis.math.domain.RuleDirection;
import de.tum.cit.aet.artemis.math.regate.dto.ConditionSpec;
import de.tum.cit.aet.artemis.math.regate.dto.RuleSpec;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/**
 * Serializes Artemis's code-only rule catalogue into the Regate protocol {@code Rule} shape. The whole
 * catalogue always travels inline in the request (leanregate has no built-in catalogue) — there is no
 * per-problem rule selection.
 * <p>
 * Rule patterns/templates are already in the protocol vocabulary (Artemis uses it natively).
 */
public final class RuleMapper {

    private static final MathNode ZERO = MathNodes.num("0");

    private RuleMapper() {
    }

    /**
     * Serializes every rule in the registry (across all blocks) to the protocol ruleset.
     *
     * @param registry the block registry holding the normalized catalogue
     * @return the full ruleset in protocol form
     */
    public static List<RuleSpec> toRuleset(BlockRegistry registry) {
        List<RuleSpec> ruleset = new ArrayList<>();
        for (BlockDefinition block : registry.getAllBlocks()) {
            for (RewriteRule rule : registry.getNormalizedRulesFor(block)) {
                ruleset.add(toRuleSpec(rule, block.getType()));
            }
        }
        return ruleset;
    }

    /**
     * Serializes a single rule to the protocol {@code Rule} shape.
     *
     * @param rule  the Artemis rule
     * @param owner the originating block type (cosmetic {@code owner} field)
     * @return the protocol rule
     */
    public static RuleSpec toRuleSpec(RewriteRule rule, String owner) {
        List<ConditionSpec> conditions = new ArrayList<>(rule.constraints().size());
        for (RuleConstraint constraint : rule.constraints()) {
            conditions.add(toCondition(constraint));
        }
        // Patterns share the protocol vocabulary natively — no translation needed.
        return new RuleSpec(rule.id(), owner, rule.pattern(), rule.template(), rule.direction() == RuleDirection.BIDIRECTIONAL, conditions);
    }

    private static ConditionSpec toCondition(RuleConstraint constraint) {
        // Artemis's sealed RuleConstraint hierarchy currently has one member: NotEqualToConstant. Every
        // existing rule uses it as `c != 0`, which is exactly the protocol's `nonzero` guard on wildcard c.
        // When Artemis grows other constraint kinds (positive/integer/constant, or a genuine `!= <non-zero>`),
        // extend this switch — the sealed hierarchy makes the compiler flag the missing case.
        return switch (constraint) {
            case NotEqualToConstant nec -> {
                MathNode value = MathNodes.normalize(nec.value());
                if (ZERO.equals(value)) {
                    yield new ConditionSpec("nonzero", nec.wildcardName(), null);
                }
                throw new UnsupportedOperationException(
                        "No Regate condition kind expresses '!= " + value + "' (only '!= 0' maps, to `nonzero`) for wildcard '" + nec.wildcardName() + "'");
            }
        };
    }
}
