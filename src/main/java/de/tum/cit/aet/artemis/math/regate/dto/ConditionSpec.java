package de.tum.cit.aet.artemis.math.regate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A side condition on a {@link RuleSpec}, as transmitted in the Regate protocol.
 * <p>
 * {@code kind} is one of {@code nonzero | positive | integer | constant | notequal}; {@code variable}
 * names the wildcard the guard applies to; {@code arg} is only meaningful for {@code notequal}.
 *
 * @param kind     the guard kind
 * @param variable the wildcard name the guard applies to (wire name {@code var})
 * @param arg      operand index for {@code notequal}, otherwise {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConditionSpec(String kind, @JsonProperty("var") String variable, Integer arg) {
}
