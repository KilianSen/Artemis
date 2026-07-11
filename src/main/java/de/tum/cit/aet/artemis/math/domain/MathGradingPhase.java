package de.tum.cit.aet.artemis.math.domain;

/**
 * The phase of asynchronous math grading a {@link MathGradingJob} represents.
 * <p>
 * The fast {@link #PRELIMINARY} pass grades every problem with its primary grader and records an immediate
 * {@code AUTOMATIC} result; if any problem configures a slow formal certifier, a follow-up {@link #CERTIFICATION}
 * pass re-grades those answers and upgrades the result (Phase 2b fast/slow lanes).
 */
public enum MathGradingPhase {

    /** The fast preliminary grade with each problem's primary grader. */
    PRELIMINARY,

    /** The slow re-grade with a formal certifier that upgrades the preliminary verdict. */
    CERTIFICATION
}
