package de.tum.cit.aet.artemis.math.regate.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.math.domain.MathNode;

/**
 * The Regate {@code GradeResponse} wire object (see {@code GRADING_PROTOCOL.md}). MathNodes in {@code proof}
 * states are in the protocol vocabulary, which Artemis uses natively — no mapping back needed.
 * <p>
 * {@code outcome} is authoritative and {@code score} is the mark: {@code score} may be {@code null} —
 * {@code equal_no_certificate} / {@code unknown} / induction-defer are inconclusive, not zeros; the caller routes
 * them to review. With partial credit on, a conclusive {@code score} is anywhere in {@code 0..100} (a value in
 * {@code 1..99} means "provably equivalent, not yet in the required form"); a {@code score} of {@code 0} alongside
 * {@code proven_equal} is "equivalent, no measurable progress" and must not be conflated with {@code proven_unequal}
 * by reading the score alone — the {@link Outcome} carries that distinction. Unknown fields are ignored so
 * backend/protocol additions (e.g. {@code meta.induction}, {@code meta.rechecked}) don't break deserialization.
 *
 * @param protocol       protocol version
 * @param backend        which backend produced this ({@code eggregate}/{@code leanregate}/{@code coqregate}/{@code cvc5regate})
 * @param backendVersion backend version string
 * @param outcome        the verdict category (authoritative)
 * @param score          {@code 0..100} | {@code null} (inconclusive → review)
 * @param certified      whether the verdict is backed by a re-checked proof
 * @param proof          the re-checkable certificate (for {@code proven_equal}), else {@code null}; always a list, but
 *                           its element shape is backend-specific (eggregate: rewrite steps; formal backends: a single
 *                           engine-artifact object). Artemis does not consume it, so unknown element fields are ignored
 * @param witness        a counterexample assignment (for {@code proven_unequal}), else {@code null}
 * @param steps          per-step statuses, else {@code null}
 * @param hint           a suggested next step, else {@code null}
 * @param feedback       human-readable feedback
 * @param meta           timing/diagnostic metadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GradeResponse(String protocol, String backend, @JsonProperty("backend_version") String backendVersion, Outcome outcome, Integer score, boolean certified,
        List<ProofStep> proof, Map<String, String> witness, List<StepStatusSpec> steps, HintSpec hint, String feedback, MetaSpec meta) {

    /** One step of a re-checkable proof. {@code state} is in protocol vocabulary. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProofStep(String rule, List<Integer> path, String direction, MathNode state) {
    }

    /** The status of a single submitted step: {@code valid} | {@code open} | {@code invalid}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StepStatusSpec(int index, String status, String reason) {
    }

    /** A suggested next step and how many remain to the goal. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HintSpec(String rule, List<Integer> path, String direction, Integer remaining) {
    }

    /** Timing / diagnostic metadata. Extra keys (e.g. {@code induction}) are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetaSpec(Integer ms, Boolean saturated, Double progress) {
    }
}
