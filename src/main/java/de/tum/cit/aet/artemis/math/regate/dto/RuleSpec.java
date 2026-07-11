package de.tum.cit.aet.artemis.math.regate.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.math.domain.MathNode;

/**
 * A rewrite rule as Regate exercise data (see {@code GRADING_PROTOCOL.md} "Rule"). {@code lhs}/{@code rhs}
 * are MathNode patterns/templates in <em>protocol</em> vocabulary (may contain {@code wild} nodes).
 *
 * @param id            unique rule id
 * @param owner         originating block type (cosmetic)
 * @param lhs           pattern (protocol vocabulary)
 * @param rhs           template (protocol vocabulary)
 * @param bidirectional whether the rule may be applied in reverse
 * @param conditions    side conditions guarding the rule
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleSpec(String id, String owner, MathNode lhs, MathNode rhs, boolean bidirectional, List<ConditionSpec> conditions) {
}
