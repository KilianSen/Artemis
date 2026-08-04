package de.tum.cit.aet.artemis.math.regate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
 * Serializes Artemis's code-only rule catalogue into the Regate protocol {@code Rule} shape. The catalogue travels
 * inline in the request (leanregate has no built-in catalogue), narrowed to the problem's allowed rule subset when
 * the instructor configured one.
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
        return toRuleset(registry, null);
    }

    /**
     * Serializes the registry's rules to the protocol ruleset, narrowed to {@code allowedRuleIds} when the problem
     * restricts the student to a subset ({@code null}/empty means the whole catalogue travels).
     * <p>
     * <b>This is defence in depth, never the enforcement point.</b> Artemis has already rejected any step citing a
     * rule outside the subset before this request is assembled (see
     * {@link de.tum.cit.aet.artemis.math.service.RuleSubsetPolicy}). Narrowing here only makes the backends reason
     * under the same rules the student had.
     * <p>
     * <b>Known consequence — a narrow subset weakens equivalence proving.</b> eggregate feeds one and the same rule
     * list to both step licensing and its equivalence oracle (e-graph saturation): the oracle can only ever fail to
     * see an equality, and reads that as {@code unknown}, never as "unequal". So the fewer rules travel, the more
     * often "is the student's expression equivalent to the target?" comes back {@code unknown} — which means more
     * submissions routed to tutor review and weaker distance-based partial credit, even for students who never
     * touched a disabled rule. That is a protocol-level trade-off (one ruleset, two jobs); do not try to fix it
     * here by widening the oracle behind the instructor's back.
     * <p>
     * Related: with {@code ac_normalization} on, eggregate injects the catalogue's {@code add_comm}/{@code add_assoc}/
     * {@code mul_comm}/{@code mul_assoc} into its oracle regardless of what travels in the ruleset. An instructor who
     * disables {@code add_comm} with AC normalisation on has therefore not really disabled commutative reasoning —
     * only the ability to <em>cite</em> the rule as a step. Both are enforced, but they are different guarantees.
     *
     * @param registry       the block registry holding the normalized catalogue
     * @param allowedRuleIds the problem's allowed rule subset, or {@code null}/empty for the whole catalogue
     * @return the ruleset in protocol form
     */
    public static List<RuleSpec> toRuleset(BlockRegistry registry, Collection<String> allowedRuleIds) {
        Set<String> allowed = allowedRuleIds == null ? Set.of() : Set.copyOf(allowedRuleIds);
        List<RuleSpec> ruleset = new ArrayList<>();
        for (BlockDefinition block : registry.getAllBlocks()) {
            for (RewriteRule rule : registry.getNormalizedRulesFor(block)) {
                if (allowed.isEmpty() || allowed.contains(rule.id())) {
                    ruleset.add(toRuleSpec(rule, block.getType()));
                }
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
