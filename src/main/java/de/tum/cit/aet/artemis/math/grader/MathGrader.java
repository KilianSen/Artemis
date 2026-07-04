package de.tum.cit.aet.artemis.math.grader;

import java.util.List;
import java.util.Optional;

import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathProblemConfig;

/**
 * Strategy interface implemented by every math-grading backend.
 * <p>
 * The current step-by-step engine is exposed as {@code RewriteChainGrader}.
 * Future M3 graders (egg e-graphs) plug in by adding a new Spring bean
 * with the appropriate {@link GraderType}; the dispatcher in
 * {@code MathGradingService} routes per-exercise.
 * <p>
 * Remote graders are first-class citizens: an implementation may forward
 * {@link #grade} to an out-of-process service (HTTP, gRPC, FFI) — the interface
 * stays in-process from the dispatcher's POV.
 */
public interface MathGrader {

    /**
     * @return the discriminator used to register and look up this grader
     */
    GraderType getType();

    /**
     * Grade a derivation (the ordered steps) against a problem configuration and return the score plus per-step status.
     *
     * @param config the problem configuration being graded (a standalone exercise or a multiplex problem)
     * @param steps  the student's ordered derivation steps
     * @return a {@link GradingResult} with score in [0, 100] and per-step status
     */
    GradingResult grade(MathProblemConfig config, List<DerivationStep> steps);

    /**
     * Suggest possible next steps the student could take from the current state.
     * Optional — used to power the "Hint" button in the workspace.
     *
     * @param config       the problem configuration being worked on
     * @param currentState the student's current math state
     * @return up to a handful of {@link HintSuggestion}s ranked by usefulness, or empty
     */
    default List<HintSuggestion> suggestHints(MathProblemConfig config, MathNode currentState) {
        return List.of();
    }

    /**
     * Run an automated reachability check from the configuration's starting expression toward its target.
     * Optional — different graders implement this differently (rewrite-chain runs a reduction strategy;
     * Lean would run {@code simp} / {@code auto}). Empty when the grader cannot answer the question.
     *
     * @param config the problem configuration to analyse
     * @return a {@link ReachabilityReport}, or empty if this grader does not support reachability checks
     */
    default Optional<ReachabilityReport> verifyReachability(MathProblemConfig config) {
        return Optional.empty();
    }
}
