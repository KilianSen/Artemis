package de.tum.cit.aet.artemis.math.grader;

import java.util.List;

/**
 * Result of grading a whole submission or a single problem. Surfaced back to the resource layer for
 * persistence and to the client (assessment view) as the verdict.
 * <p>
 * {@code conclusive} distinguishes a decided verdict (a real score) from an honestly inconclusive one: a
 * remote backend may answer {@code equal_no_certificate} / {@code unknown} / induction-defer, which must
 * route to review rather than collapse to a zero. When {@code conclusive} is false, {@code score} is
 * {@link Double#NaN} and callers must not treat it as a grade. {@code outcome} and {@code certified} carry the
 * remote backend's verdict category and whether it is backed by a re-checked proof; {@code witness} is a
 * human-readable counterexample assignment for a {@code proven_unequal} verdict (all empty/false/null for the
 * in-process rewrite engine).
 *
 * @param score        final score in [0, 100], or {@link Double#NaN} when {@code conclusive} is false
 * @param conclusive   whether the grader reached a decided verdict (false ⇒ route to review)
 * @param outcome      the backend verdict category (e.g. {@code PROVEN_EQUAL}), or {@code null} for the in-process grader
 * @param certified    whether the verdict is backed by a re-checked proof
 * @param witness      a formatted counterexample (e.g. {@code "x=0, y=1"}) for a disproof, or {@code null}
 * @param stepStatuses per-step status, in submission order
 * @param message      optional grader-specific narrative / feedback ({@code null} if none)
 */
public record GradingResult(double score, boolean conclusive, String outcome, boolean certified, String witness, List<StepStatus> stepStatuses, String message) {

    public GradingResult {
        stepStatuses = stepStatuses == null ? List.of() : List.copyOf(stepStatuses);
    }

    /** A conclusive, self-contained score from an in-process or fully-decided grader. */
    public static GradingResult of(double score) {
        return new GradingResult(score, true, null, false, null, List.of(), null);
    }

    /** An honestly inconclusive verdict — route to review, never a zero. */
    public static GradingResult inconclusive(String message) {
        return new GradingResult(Double.NaN, false, null, false, null, List.of(), message);
    }
}
