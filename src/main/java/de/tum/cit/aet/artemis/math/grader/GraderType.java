package de.tum.cit.aet.artemis.math.grader;

import java.util.EnumSet;
import java.util.Set;

import de.tum.cit.aet.artemis.math.domain.GoalMode;

/**
 * Discriminator used to dispatch a {@link MathGrader} for a given problem, plus the static capabilities the
 * authoring layer needs to validate a problem: latency {@link GradingSpeed} (fast/slow lane), whether the
 * grader is a remote Regate backend, and which {@link GoalMode}s it can grade conclusively.
 * <p>
 * {@link #REWRITE_CHAIN} is the in-process engine and the default fallback. The four Regate backends are
 * reached over HTTP; they all speak the same protocol, differing in engine and the modes they certify:
 * <ul>
 * <li>{@link #EGGREGATE} — egglog e-graph; transformation/equation (defers induction).</li>
 * <li>{@link #LEANREGATE} — Lean formal; transformation/equation/induction (certifies).</li>
 * <li>{@link #COQREGATE} — Rocq/Coq; induction only.</li>
 * <li>{@link #CVC5REGATE} — cvc5 SMT; induction only.</li>
 * </ul>
 */
public enum GraderType {

    /** Step-by-step structural rewriting against the fixed rule library, in-process. The default. */
    REWRITE_CHAIN(GradingSpeed.FAST, false, EnumSet.of(GoalMode.TRANSFORMATION, GoalMode.EQUATION)),

    /** Regate eggregate backend (egglog equality saturation + proof-producing e-graph). */
    EGGREGATE(GradingSpeed.FAST, true, EnumSet.of(GoalMode.TRANSFORMATION, GoalMode.EQUATION)),

    /** Regate leanregate backend (Lean formal proofs); the only backend that certifies induction. */
    LEANREGATE(GradingSpeed.SLOW, true, EnumSet.of(GoalMode.TRANSFORMATION, GoalMode.EQUATION, GoalMode.INDUCTION)),

    /** Regate coqregate backend (Rocq/Coq); a specialist induction certifier. */
    COQREGATE(GradingSpeed.SLOW, true, EnumSet.of(GoalMode.INDUCTION)),

    /** Regate cvc5regate backend (cvc5 SMT with structural induction); a specialist induction certifier. */
    CVC5REGATE(GradingSpeed.FAST, true, EnumSet.of(GoalMode.INDUCTION));

    private final GradingSpeed speed;

    private final boolean remote;

    private final Set<GoalMode> supportedModes;

    GraderType(GradingSpeed speed, boolean remote, Set<GoalMode> supportedModes) {
        this.speed = speed;
        this.remote = remote;
        this.supportedModes = Set.copyOf(supportedModes);
    }

    public GradingSpeed getSpeed() {
        return speed;
    }

    /** @return whether this grader is a remote Regate backend (as opposed to the in-process rewrite engine). */
    public boolean isRemote() {
        return remote;
    }

    public Set<GoalMode> getSupportedModes() {
        return supportedModes;
    }

    /**
     * @param mode the goal mode of a problem
     * @return whether this grader can grade that mode conclusively (used for backend↔mode authoring validation)
     */
    public boolean supports(GoalMode mode) {
        return supportedModes.contains(mode);
    }
}
