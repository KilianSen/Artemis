package de.tum.cit.aet.artemis.math.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.assessment.service.ComplaintResponseService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.MathAssessmentUpdateDTO;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;

/**
 * Tutor-facing manual assessment of math submissions: the review queue, soft locking, draft/submit of the manual
 * score + feedback, and cancellation.
 * <p>
 * Math grades automatically; a submission needs manual assessment only when automatic grading was inconclusive or
 * failed and it carries no result (see {@code MathGradingDispatcher}). Those are the assessable submissions. Locking
 * reuses the shared mechanism (an empty {@code MANUAL} {@link Result} with an assessor and no completion date), so a
 * second tutor is not offered the same submission and the standard assessment-dashboard stats work unchanged. Unlike
 * text/modeling, the manual <em>score</em> is authoritative and set directly (not summed from feedback credits); the
 * feedback is descriptive.
 */
@Lazy
@Service
@Conditional(MathEnabled.class)
public class MathAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(MathAssessmentService.class);

    private final MathSubmissionRepository mathSubmissionRepository;

    private final ResultRepository resultRepository;

    private final SubmissionService submissionService;

    private final AssessmentService assessmentService;

    private final ComplaintResponseService complaintResponseService;

    public MathAssessmentService(MathSubmissionRepository mathSubmissionRepository, ResultRepository resultRepository, SubmissionService submissionService,
            AssessmentService assessmentService, ComplaintResponseService complaintResponseService) {
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.resultRepository = resultRepository;
        this.submissionService = submissionService;
        this.assessmentService = assessmentService;
        this.complaintResponseService = complaintResponseService;
    }

    /**
     * The next submission eligible for a new manual assessment (submitted, unassessed, unlocked), oldest first, or empty
     * if none remain.
     *
     * @param exerciseId the exercise to pull from
     * @return the oldest assessable submission, if any
     */
    public Optional<MathSubmission> getAssessableSubmission(long exerciseId) {
        List<MathSubmission> assessable = mathSubmissionRepository.findAssessableByExerciseId(exerciseId);
        return assessable.isEmpty() ? Optional.empty() : Optional.of(assessable.getFirst());
    }

    /**
     * Locks the next assessable submission for the given tutor and returns it, or empty if none remain. Locking creates
     * an empty {@code MANUAL} result assigned to the tutor so no other tutor is offered the same submission.
     *
     * @param exerciseId the exercise to pull from
     * @param tutor      the tutor acquiring the lock
     * @return the locked submission, if one was available
     */
    public Optional<MathSubmission> lockAndGetAssessableSubmission(long exerciseId, User tutor) {
        Optional<MathSubmission> assessable = getAssessableSubmission(exerciseId);
        if (assessable.isEmpty()) {
            return Optional.empty();
        }
        // Reload with the results collection initialized before locking — the assessable query does not fetch the
        // (empty) results, so touching it in a detached state would trip a LazyInitializationException.
        MathSubmission submission = mathSubmissionRepository.findByIdWithAnswersResultsAndParticipation(assessable.get().getId()).orElseThrow();
        return Optional.of(lockSubmission(submission, exerciseId, tutor));
    }

    /**
     * Soft-locks a submission for a tutor by attaching (or reusing) an empty {@code MANUAL} result with the tutor as
     * assessor and no completion date.
     *
     * @param submission the submission to lock
     * @param exerciseId the exercise the submission belongs to
     * @param tutor      the assessor
     * @return the submission, now carrying the lock result
     */
    public MathSubmission lockSubmission(MathSubmission submission, long exerciseId, User tutor) {
        Result result = inProgressManualResult(submission);
        if (result == null) {
            result = submissionService.saveNewEmptyResult(submission, exerciseId);
        }
        result.setAssessor(tutor);
        result.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.save(result);
        log.debug("Tutor {} locked math submission {}", tutor.getLogin(), submission.getId());
        return submission;
    }

    /**
     * Records the tutor's manual score and feedback on the submission's manual result. A draft ({@code submit=false})
     * keeps the lock and stays invisible to the student (no completion date); submitting finalizes it.
     *
     * @param submission the submission being assessed (with its results loaded)
     * @param exercise   the exercise (for course-aware score rounding)
     * @param score      the manual score in [0, 100]
     * @param feedbacks  the tutor's (unreferenced) feedback, may be empty
     * @param submit     whether to finalize the assessment ({@code true}) or save a draft ({@code false})
     * @param tutor      the assessor
     * @return the manual result after the update
     */
    public Result saveManualAssessment(MathSubmission submission, MathExercise exercise, double score, List<Feedback> feedbacks, boolean submit, User tutor) {
        Result result = inProgressManualResult(submission);
        if (result == null) {
            result = new Result();
            result.setSubmission(submission);
            result.setAssessmentType(AssessmentType.MANUAL);
            result.setExerciseId(exercise.getId());
            submission.addResult(result);
        }
        else if (result.getId() != null && !Hibernate.isInitialized(result.getFeedbacks())) {
            // The lock/draft result was loaded without its feedbacks; initialize them so updateAllFeedbackItems below
            // does not trip a LazyInitializationException on this non-transactional path.
            final Result existing = result;
            resultRepository.findByIdWithEagerFeedbacks(existing.getId()).ifPresent(loaded -> existing.setFeedbacks(loaded.getFeedbacks()));
        }
        result.setAssessor(tutor);
        result.setAssessmentType(AssessmentType.MANUAL);
        result.setScore(score, exercise.getCourseViaExerciseGroupOrCourseMember());
        // Math's manual score is authoritative — feedback is descriptive, not credit-summed (unlike text/file-upload).
        result.updateAllFeedbackItems(feedbacks == null ? List.of() : feedbacks, false);
        if (submit) {
            result.setCompletionDate(ZonedDateTime.now());
            result.setRated(true);
        }
        else {
            // Draft: keep the lock, stay invisible to the student.
            result.setCompletionDate(null);
        }
        return resultRepository.save(result);
    }

    /**
     * Cancels an in-progress assessment, releasing the lock by deleting the tutor's draft/empty result (a completed
     * manual result or the automatic result is left untouched).
     *
     * @param submission the submission whose assessment to cancel
     */
    public void cancelAssessment(MathSubmission submission) {
        assessmentService.cancelAssessmentOfSubmission(submission);
    }

    /**
     * Resolves a student's complaint and applies the tutor's (possibly revised) manual assessment. The shared
     * {@link ComplaintResponseService} records the response and enforces the responder rules (a complaint may not be
     * resolved by the original assessor; instructors always may). The revised score + feedback are then written as the
     * final assessment, with the responding tutor as assessor.
     *
     * @param submission the submission the complaint targets (with its results loaded)
     * @param exercise   the exercise (for course-aware score rounding)
     * @param update     the complaint response plus the revised score and feedback
     * @param tutor      the responding tutor/instructor
     * @return the updated manual result
     */
    public Result updateAfterComplaint(MathSubmission submission, MathExercise exercise, MathAssessmentUpdateDTO update, User tutor) {
        // Records the ComplaintResponse and marks the complaint accepted/rejected, enforcing the shared responder rules.
        complaintResponseService.resolveComplaint(update.complaintResponse());
        // Apply the tutor's revised score + feedback as the authoritative final assessment.
        return saveManualAssessment(submission, exercise, update.score(), update.feedbacks(), true, tutor);
    }

    /** The submission's latest {@code MANUAL} result that is still a draft/lock (no completion date), or {@code null}. */
    private Result inProgressManualResult(MathSubmission submission) {
        if (submission.getResults() == null) {
            return null;
        }
        return submission.getResults().stream().filter(result -> result.getAssessmentType() == AssessmentType.MANUAL).reduce((first, second) -> second).orElse(null);
    }
}
