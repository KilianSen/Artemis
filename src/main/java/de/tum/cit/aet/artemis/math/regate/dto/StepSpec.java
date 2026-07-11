package de.tum.cit.aet.artemis.math.regate.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.math.domain.MathNode;

/**
 * One step of a submitted derivation (see {@code GRADING_PROTOCOL.md} {@code submission.steps}).
 * <p>
 * {@code path} indexes the rewrite site in <em>alphabetical slot order</em>; an empty list targets the root
 * and MUST be transmitted (never dropped), so this DTO uses {@code NON_NULL}, not {@code NON_EMPTY}.
 * {@code kind} is {@code "A"} (rule application) or {@code "B"} (Leibniz substitution); {@code equation} is
 * present only for kind {@code B}. All MathNodes are in protocol vocabulary.
 *
 * @param rule      applied rule id
 * @param path      path to the rewrite site (alphabetical slot order; {@code []} = root)
 * @param direction {@code "forward"} or {@code "reverse"}
 * @param kind      {@code "A"} or {@code "B"}
 * @param equation  the substituted equality for kind {@code B}, otherwise {@code null}
 * @param result    the full tree after this step (protocol vocabulary)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepSpec(String rule, List<Integer> path, String direction, String kind, List<MathNode> equation, MathNode result) {
}
