package de.tum.cit.aet.artemis.math.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import de.tum.cit.aet.artemis.math.grader.GradingSpeed;

/**
 * A durable record of one asynchronous grading pass over a {@link MathSubmission} by a remote (Regate) grader.
 * <p>
 * Remote grading runs off the request thread on a {@link GradingSpeed fast or slow} executor. Persisting the job
 * (rather than keeping only the in-memory {@code @Async} task) makes it crash-recoverable: after a restart the
 * {@code MathGradingRecoveryService} finds jobs left {@link MathGradingJobStatus#PENDING} and re-dispatches them,
 * so a submission is never stuck without a result. The {@link #version optimistic-lock version} lets several nodes
 * safely race to recover the same job — only one claim wins.
 * <p>
 * The submission and exercise are referenced by id (not JPA associations) to keep the row light and avoid pulling
 * the grading graph into every recovery scan.
 */
@Entity
@Table(name = "math_grading_job")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathGradingJob extends DomainObject {

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    /** The grader that owns this pass — the primary grader for {@link MathGradingPhase#PRELIMINARY}, the certifier for {@link MathGradingPhase#CERTIFICATION}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "grader_type", length = 32)
    private GraderType graderType;

    /** Which executor lane owns this pass (fast preliminary vs. slow certification). */
    @Enumerated(EnumType.STRING)
    @Column(name = "lane", length = 16, nullable = false)
    private GradingSpeed lane;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 16, nullable = false)
    private MathGradingPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private MathGradingJobStatus status = MathGradingJobStatus.PENDING;

    /** Number of times this job has been dispatched (initial dispatch + recovery re-dispatches), capped to bound retries. */
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    /** When the current dispatch started grading; used to detect a job stuck in flight after a crash. */
    @Column(name = "started_date")
    private Instant startedDate;

    @Column(name = "finished_date")
    private Instant finishedDate;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Version
    @Column(name = "version")
    private Long version;

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public GraderType getGraderType() {
        return graderType;
    }

    public void setGraderType(GraderType graderType) {
        this.graderType = graderType;
    }

    public GradingSpeed getLane() {
        return lane;
    }

    public void setLane(GradingSpeed lane) {
        this.lane = lane;
    }

    public MathGradingPhase getPhase() {
        return phase;
    }

    public void setPhase(MathGradingPhase phase) {
        this.phase = phase;
    }

    public MathGradingJobStatus getStatus() {
        return status;
    }

    public void setStatus(MathGradingJobStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public Instant getStartedDate() {
        return startedDate;
    }

    public void setStartedDate(Instant startedDate) {
        this.startedDate = startedDate;
    }

    public Instant getFinishedDate() {
        return finishedDate;
    }

    public void setFinishedDate(Instant finishedDate) {
        this.finishedDate = finishedDate;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Long getVersion() {
        return version;
    }
}
