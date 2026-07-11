package de.tum.cit.aet.artemis.math.regate.dto;

import de.tum.cit.aet.artemis.math.domain.MathNode;

/**
 * A declared fact that discharges a guarded rule's symbolic side condition (see {@code GRADING_PROTOCOL.md}),
 * e.g. {@code {kind: "nonzero", value: x}} to let a {@code x/x → 1} step fire. {@code value} is in protocol
 * vocabulary.
 *
 * @param kind  one of {@code nonzero | positive | integer | constant}
 * @param value the term the fact is asserted about (protocol vocabulary)
 */
public record AssumptionSpec(String kind, MathNode value) {
}
