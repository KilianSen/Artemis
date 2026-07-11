package de.tum.cit.aet.artemis.math.domain;

/**
 * Lifecycle state of a {@link MathGradingJob}. The job row is the durable source of truth for "is this
 * submission being graded" so a server restart mid-grade can recover instead of leaving the submission stuck.
 */
public enum MathGradingJobStatus {

    /** Enqueued or in flight: the async pass has not (successfully) finished yet. Recoverable if the node died. */
    PENDING,

    /** The grader returned a conclusive verdict and the authoritative result was recorded. */
    COMPLETED,

    /** The grader returned an inconclusive verdict; the submission is left for manual review (no automatic result). */
    REVIEW,

    /** The grader failed (backend error after retries, or max recovery attempts exhausted); left for manual review. */
    FAILED
}
