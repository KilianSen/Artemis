package de.tum.cit.aet.artemis.math.regate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.math.grader.GradingResult;
import de.tum.cit.aet.artemis.math.grader.HintSuggestion;
import de.tum.cit.aet.artemis.math.grader.StepStatus;
import de.tum.cit.aet.artemis.math.regate.dto.GradeResponse;

/**
 * Maps a Regate {@link GradeResponse} to a {@link GradingResult}. The backend's {@code outcome} is authoritative
 * and its {@code score} is the mark: a null score is an honestly inconclusive verdict (equal_no_certificate /
 * unknown / induction-defer) that routes to review — never a zero. A non-null score is conclusive and taken
 * verbatim, including partial-credit values in {@code 1..99} (equivalent but not yet in the required form) and a
 * conclusive {@code 0} (e.g. {@code proven_unequal}, {@code invalid_derivation}, or equivalent-with-no-progress).
 */
public final class RegateResponseMapper {

    private RegateResponseMapper() {
    }

    /**
     * Extracts the backend's next-step suggestion (from a {@code want_hint} response) as at most one
     * {@link HintSuggestion}. The backend returns the rule and path but not the resulting state, so
     * {@code previewResult} is {@code null}.
     *
     * @param response the backend grade response
     * @return a single-element list with the suggested rule, or empty if the backend offered no hint
     */
    public static List<HintSuggestion> toHints(GradeResponse response) {
        GradeResponse.HintSpec hint = response.hint();
        if (hint == null || hint.rule() == null) {
            return List.of();
        }
        String rationale = hint.remaining() == null ? null : hint.remaining() + " step(s) to the goal";
        return List.of(new HintSuggestion(hint.rule(), hint.path(), null, rationale));
    }

    /**
     * Maps a {@link GradeResponse} to a {@link GradingResult}. A null score becomes an inconclusive verdict
     * (route to review), never a zero; any non-null score (including partial credit and a conclusive zero) is
     * taken verbatim as a conclusive mark. The {@code outcome} travels through so downstream can tell a
     * conclusive zero apart from an inconclusive review case.
     *
     * @param response the backend grade response
     * @return the grading result (conclusive with a score, or inconclusive)
     */
    public static GradingResult toGradingResult(GradeResponse response) {
        String outcome = response.outcome() == null ? null : response.outcome().name();
        boolean certified = response.certified();
        String witness = formatWitness(response.witness());
        Integer score = response.score();
        if (score == null) {
            return new GradingResult(Double.NaN, false, outcome, certified, witness, List.of(), response.feedback());
        }
        return new GradingResult(score, true, outcome, certified, witness, toStepStatuses(response), response.feedback());
    }

    /** Formats a counterexample assignment map as a compact {@code "x=0, y=1"} string, or {@code null} if none. */
    private static String formatWitness(Map<String, String> witness) {
        if (witness == null || witness.isEmpty()) {
            return null;
        }
        return witness.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
    }

    private static List<StepStatus> toStepStatuses(GradeResponse response) {
        if (response.steps() == null) {
            return List.of();
        }
        // Protocol step status is valid | open | invalid; only "valid" counts as accepted.
        return response.steps().stream().map(s -> {
            boolean valid = "valid".equals(s.status());
            return new StepStatus(s.index(), valid, valid ? null : s.reason());
        }).toList();
    }
}
