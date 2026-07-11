package de.tum.cit.aet.artemis.math.regate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The verdict category of a Regate {@code GradeResponse} (see {@code GRADING_PROTOCOL.md}).
 * <p>
 * Only {@link #PROVEN_EQUAL} (with a checked proof) and {@link #PROVEN_UNEQUAL} (with a witness) are
 * conclusive. {@link #EQUAL_NO_CERTIFICATE} and {@link #UNKNOWN} are honestly inconclusive — the caller
 * routes them to review, never to a zero. {@link #INVALID_DERIVATION} means a submitted step is not a
 * valid rule application.
 */
public enum Outcome {

    @JsonProperty("proven_equal")
    PROVEN_EQUAL,

    @JsonProperty("proven_unequal")
    PROVEN_UNEQUAL,

    @JsonProperty("equal_no_certificate")
    EQUAL_NO_CERTIFICATE,

    @JsonProperty("invalid_derivation")
    INVALID_DERIVATION,

    @JsonProperty("unknown")
    UNKNOWN
}
