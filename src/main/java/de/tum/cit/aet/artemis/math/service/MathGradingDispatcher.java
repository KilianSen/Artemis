package de.tum.cit.aet.artemis.math.service;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathGradingJob;
import de.tum.cit.aet.artemis.math.domain.MathGradingJobStatus;
import de.tum.cit.aet.artemis.math.domain.MathGradingPhase;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.MathGradingStatusDTO;
import de.tum.cit.aet.artemis.math.grader.GradingResult;
import de.tum.cit.aet.artemis.math.grader.GradingSpeed;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.repository.MathGradingJobRepository;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;

/**
 * Grades a submitted math submission <em>off the request thread</em> when it uses a remote (Regate) grader.
 * <p>
 * Each asynchronous pass is backed by a durable {@link MathGradingJob} row (Phase 2b + #1 durability): the
 * <b>fast</b> preliminary pass ({@code mathFastGradingExecutor}) grades every problem with its primary grader and
 * records a preliminary {@code AUTOMATIC} result immediately; if any problem configures a
 * {@link MathProblem#getCertifyingGraderType() certifier}, the <b>slow</b> certification pass
 * ({@code mathSlowGradingExecutor}) then re-grades those answers with the formal prover and upgrades the result.
 * In-process {@code REWRITE_CHAIN} grading stays synchronous in {@code MathSubmissionResource}.
 * <p>
 * Because the job is persisted, a server restart mid-grade does not strand the submission: the
 * {@link MathGradingRecoveryService} re-dispatches any job left {@link MathGradingJobStatus#PENDING}. When a pass
 * records a result it is pushed to the participant over the standard result websocket, so the client no longer polls.
 */
@Lazy
@Service
@Conditional(MathEnabled.class)
public class MathGradingDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MathGradingDispatcher.class);

    /** Per-user websocket destination for terminal grading states that have no result (escalated to manual review). */
    public static final String MATH_GRADING_STATUS_TOPIC = "/topic/math-grading-status";

    /** Cap the persisted failure reason so a verbose backend stack trace never overflows the column. */
    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final MathGradingService mathGradingService;

    private final MathExerciseRepository mathExerciseRepository;

    private final MathSubmissionRepository mathSubmissionRepository;

    private final MathGradingJobRepository mathGradingJobRepository;

    private final ResultRepository resultRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultWebsocketService resultWebsocketService;

    private final WebsocketMessagingService websocketMessagingService;

    // Self-proxy so an enqueue can invoke the @Async method through Spring's proxy (a direct this.call would run inline).
    private final MathGradingDispatcher self;

    public MathGradingDispatcher(MathGradingService mathGradingService, MathExerciseRepository mathExerciseRepository, MathSubmissionRepository mathSubmissionRepository,
            MathGradingJobRepository mathGradingJobRepository, ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository,
            ResultWebsocketService resultWebsocketService, WebsocketMessagingService websocketMessagingService, @Lazy MathGradingDispatcher self) {
        this.mathGradingService = mathGradingService;
        this.mathExerciseRepository = mathExerciseRepository;
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.mathGradingJobRepository = mathGradingJobRepository;
        this.resultRepository = resultRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultWebsocketService = resultWebsocketService;
        this.websocketMessagingService = websocketMessagingService;
        this.self = self;
    }

    /**
     * Enqueues a durable {@link MathGradingPhase#PRELIMINARY} grading job and fires the fast async pass. The submission
     * must already be persisted. This is the single entry point from the submit path.
     *
     * @param exerciseId   the math exercise id
     * @param submissionId the persisted submission id to grade
     */
    public void dispatch(long exerciseId, long submissionId) {
        MathGradingJob job = enqueue(exerciseId, submissionId, MathGradingPhase.PRELIMINARY, GradingSpeed.FAST);
        self.gradeAsync(exerciseId, submissionId, job.getId());
    }

    /**
     * Fast lane: grades the submission with each problem's primary grader, records the preliminary result, pushes it to
     * the participant, and — if any problem has a certifier — chains the slow certification pass.
     *
     * @param exerciseId   the math exercise id
     * @param submissionId the persisted submission id to grade
     * @param jobId        the durable job tracking this pass
     */
    @Async("mathFastGradingExecutor")
    public void gradeAsync(long exerciseId, long submissionId, long jobId) {
        runPass(exerciseId, submissionId, jobId, false);
    }

    /**
     * Slow lane: re-grades the certifier problems with the formal prover and upgrades the result. Runs on the isolated
     * slow pool so a multi-second proof never starves fast grading.
     *
     * @param exerciseId   the math exercise id
     * @param submissionId the persisted submission id to certify
     * @param jobId        the durable job tracking this pass
     */
    @Async("mathSlowGradingExecutor")
    public void certifyAsync(long exerciseId, long submissionId, long jobId) {
        runPass(exerciseId, submissionId, jobId, true);
    }

    /**
     * Re-dispatches an existing job (used by the recovery scheduler). Fires the matching async pass for the job's phase.
     *
     * @param job the (already claimed) job to re-run
     */
    public void redispatch(MathGradingJob job) {
        if (job.getPhase() == MathGradingPhase.CERTIFICATION) {
            self.certifyAsync(job.getExerciseId(), job.getSubmissionId(), job.getId());
        }
        else {
            self.gradeAsync(job.getExerciseId(), job.getSubmissionId(), job.getId());
        }
    }

    /** Upserts a PENDING job for the given phase (reusing an existing row for the same submission+phase, e.g. on resubmit). */
    private MathGradingJob enqueue(long exerciseId, long submissionId, MathGradingPhase phase, GradingSpeed lane) {
        MathGradingJob job = mathGradingJobRepository.findBySubmissionIdAndPhase(submissionId, phase).orElseGet(MathGradingJob::new);
        job.setSubmissionId(submissionId);
        job.setExerciseId(exerciseId);
        job.setPhase(phase);
        job.setLane(lane);
        job.setStatus(MathGradingJobStatus.PENDING);
        job.setAttempts(1);
        job.setCreatedDate(Instant.now());
        job.setStartedDate(null);
        job.setFinishedDate(null);
        job.setFailureReason(null);
        return mathGradingJobRepository.save(job);
    }

    /** Executes one grading pass, transitioning the durable job through its lifecycle and pushing the result on success. */
    private void runPass(long exerciseId, long submissionId, long jobId, boolean certify) {
        MathGradingJob job = mathGradingJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Async math grading skipped: job {} not found", jobId);
            return;
        }
        // Claim this pass by stamping the start time; if another node already claimed it (recovery race), the optimistic
        // lock trips and we bow out.
        job.setStartedDate(Instant.now());
        try {
            job = mathGradingJobRepository.save(job);
        }
        catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Math grading job {} already claimed by another worker; skipping", jobId);
            return;
        }

        MathExercise exercise = mathExerciseRepository.findByIdWithCategoriesAndCourseAndProblems(exerciseId).orElse(null);
        MathSubmission submission = mathSubmissionRepository.findByIdWithAnswersResultsAndParticipation(submissionId).orElse(null);
        if (exercise == null || submission == null) {
            log.warn("Async math grading skipped: exercise {} or submission {} not found", exerciseId, submissionId);
            failJob(job, "Exercise or submission not found");
            return;
        }
        try {
            GradingResult grading = certify ? mathGradingService.certifySubmission(exercise, submission) : mathGradingService.gradeSubmission(exercise, submission);
            mathSubmissionRepository.save(submission);
            Result result = upsertResult(exercise, submission, grading, exerciseId);
            log.debug("{} math grading of submission {}: {}", certify ? "Slow certification" : "Fast", submissionId, grading.conclusive() ? grading.score() : "inconclusive");
            if (result != null) {
                broadcastResult(exercise, submission, result);
            }
            completeJob(job, grading);

            boolean willCertify = !certify && hasCertifier(exercise);
            if (willCertify) {
                MathGradingJob certJob = enqueue(exerciseId, submissionId, MathGradingPhase.CERTIFICATION, GradingSpeed.SLOW);
                self.certifyAsync(exerciseId, submissionId, certJob.getId());
            }
            else if (!grading.conclusive()) {
                // Terminal inconclusive verdict with no further certification pass — escalate to manual tutor review and tell the student.
                broadcastGradingStatus(exercise, submission, MathGradingJobStatus.REVIEW);
            }
        }
        catch (Exception e) {
            log.error("Async math grading of submission {} failed", submissionId, e);
            failJob(job, e.getMessage());
            broadcastGradingStatus(exercise, submission, MathGradingJobStatus.FAILED);
        }
    }

    private void completeJob(MathGradingJob job, GradingResult grading) {
        job.setStatus(grading.conclusive() ? MathGradingJobStatus.COMPLETED : MathGradingJobStatus.REVIEW);
        job.setFinishedDate(Instant.now());
        saveJobQuietly(job);
    }

    private void failJob(MathGradingJob job, String reason) {
        job.setStatus(MathGradingJobStatus.FAILED);
        job.setFinishedDate(Instant.now());
        if (reason != null) {
            job.setFailureReason(reason.length() > MAX_FAILURE_REASON_LENGTH ? reason.substring(0, MAX_FAILURE_REASON_LENGTH) : reason);
        }
        saveJobQuietly(job);
    }

    /** Persists a terminal job transition, tolerating an optimistic-lock loss to a concurrent recovery claim. */
    private void saveJobQuietly(MathGradingJob job) {
        try {
            mathGradingJobRepository.save(job);
        }
        catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Could not persist terminal state of math grading job {} (claimed concurrently)", job.getId());
        }
    }

    /** Pushes the recorded result to the participant over the standard result websocket (replacing client polling). */
    private void broadcastResult(MathExercise exercise, MathSubmission submission, Result result) {
        StudentParticipation participation = loadParticipationForBroadcast(exercise, submission);
        if (participation == null) {
            return;
        }
        // Reload the result with its feedbacks eagerly initialized: the websocket payload iterates them, which would
        // otherwise trip a LazyInitializationException on this detached async thread.
        Result toBroadcast = resultRepository.findByIdWithEagerFeedbacks(result.getId()).orElse(result);
        submission.setParticipation(participation);
        toBroadcast.setSubmission(submission);
        resultWebsocketService.broadcastNewResult(participation, toBroadcast);
    }

    /**
     * Pushes a terminal grading status that has no automatic result (the submission was escalated to manual review) to
     * the participant, so the editor can show an "awaiting tutor review" state without polling. {@code COMPLETED} is
     * conveyed by the result push instead, so only {@code REVIEW}/{@code FAILED} are sent here.
     */
    private void broadcastGradingStatus(MathExercise exercise, MathSubmission submission, MathGradingJobStatus status) {
        StudentParticipation participation = loadParticipationForBroadcast(exercise, submission);
        if (participation == null) {
            return;
        }
        String login = participation.getStudent().map(User::getLogin).orElse(null);
        if (login == null) {
            return;
        }
        websocketMessagingService.sendMessageToUser(login, MATH_GRADING_STATUS_TOPIC, new MathGradingStatusDTO(submission.getId(), participation.getId(), status));
    }

    /** Loads the submission's participation with its student + the fully-loaded (course-bearing) exercise, for a websocket broadcast on this detached async thread. */
    private StudentParticipation loadParticipationForBroadcast(MathExercise exercise, MathSubmission submission) {
        if (!(submission.getParticipation() instanceof StudentParticipation participationRef) || participationRef.getId() == null) {
            return null;
        }
        StudentParticipation participation = studentParticipationRepository.findWithStudentAndExerciseById(participationRef.getId()).orElse(null);
        if (participation == null) {
            return null;
        }
        // The websocket payload projects the participation's exercise (incl. its course) — attach the fully-loaded
        // exercise so serialization does not hit a LazyInitializationException here.
        participation.setExercise(exercise);
        return participation;
    }

    private boolean hasCertifier(MathExercise exercise) {
        return exercise.getProblems() != null && exercise.getProblems().stream().anyMatch(problem -> problem.getCertifyingGraderType() != null);
    }

    /**
     * Creates or updates the authoritative {@code AUTOMATIC} result for a conclusive verdict and returns it; an
     * inconclusive verdict leaves the submission pending/for review and returns {@code null}.
     */
    private Result upsertResult(MathExercise exercise, MathSubmission submission, GradingResult grading, long exerciseId) {
        if (!grading.conclusive()) {
            return null;
        }
        Result result = submission.getResults() == null ? null
                : submission.getResults().stream().filter(existing -> existing.getAssessmentType() == AssessmentType.AUTOMATIC).findFirst().orElse(null);
        if (result == null) {
            result = new Result();
            result.setSubmission(submission);
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            result.setRated(true);
            result.setExerciseId(exerciseId);
            submission.addResult(result);
        }
        result.setCompletionDate(ZonedDateTime.now());
        result.setScore(grading.score(), exercise.getCourseViaExerciseGroupOrCourseMember());
        return resultRepository.save(result);
    }
}
