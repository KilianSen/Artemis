package de.tum.cit.aet.artemis.math.service;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.grader.GradingResult;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;

/**
 * Grades a submitted math submission <em>off the request thread</em> when it uses a remote (Regate) grader.
 * Phase 2b fast/slow lanes: the <b>fast</b> pass ({@code mathFastGradingExecutor}) grades every problem with its
 * primary grader and records a preliminary {@code AUTOMATIC} result immediately; if any problem configures a
 * {@link MathProblem#getCertifyingGraderType() certifier}, the <b>slow</b> pass ({@code mathSlowGradingExecutor})
 * then re-grades those answers with the formal prover and upgrades the result to certified. In-process
 * {@code REWRITE_CHAIN} grading stays synchronous in {@code MathSubmissionResource}. The client polls the
 * submission until the result lands and, for certifier problems, until certification completes.
 */
@Lazy
@Service
@Conditional(MathEnabled.class)
public class MathGradingDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MathGradingDispatcher.class);

    private final MathGradingService mathGradingService;

    private final MathExerciseRepository mathExerciseRepository;

    private final MathSubmissionRepository mathSubmissionRepository;

    private final ResultRepository resultRepository;

    // Self-proxy so the fast pass can invoke the slow pass through Spring's async proxy (a direct this.call would run inline).
    private final MathGradingDispatcher self;

    public MathGradingDispatcher(MathGradingService mathGradingService, MathExerciseRepository mathExerciseRepository, MathSubmissionRepository mathSubmissionRepository,
            ResultRepository resultRepository, @Lazy MathGradingDispatcher self) {
        this.mathGradingService = mathGradingService;
        this.mathExerciseRepository = mathExerciseRepository;
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.resultRepository = resultRepository;
        this.self = self;
    }

    /**
     * Fast lane: grades the submission with each problem's primary grader, records the preliminary result, and — if
     * any problem has a certifier — chains the slow certification pass. The submission must already be persisted.
     *
     * @param exerciseId   the math exercise id
     * @param submissionId the persisted submission id to grade
     */
    @Async("mathFastGradingExecutor")
    public void gradeAsync(long exerciseId, long submissionId) {
        MathExercise exercise = mathExerciseRepository.findByIdWithCategoriesAndCourseAndProblems(exerciseId).orElse(null);
        MathSubmission submission = mathSubmissionRepository.findByIdWithAnswersAndResults(submissionId).orElse(null);
        if (exercise == null || submission == null) {
            log.warn("Async math grading skipped: exercise {} or submission {} not found", exerciseId, submissionId);
            return;
        }
        GradingResult grading = mathGradingService.gradeSubmission(exercise, submission);
        mathSubmissionRepository.save(submission);
        upsertResult(exercise, submission, grading, exerciseId);
        log.debug("Fast math grading of submission {}: {}", submissionId, grading.conclusive() ? grading.score() : "inconclusive");

        if (hasCertifier(exercise)) {
            self.certifyAsync(exerciseId, submissionId);
        }
    }

    /**
     * Slow lane: re-grades the certifier problems with the formal prover and upgrades the result. Runs on the
     * isolated slow pool so a multi-second proof never starves fast grading.
     *
     * @param exerciseId   the math exercise id
     * @param submissionId the persisted submission id to certify
     */
    @Async("mathSlowGradingExecutor")
    public void certifyAsync(long exerciseId, long submissionId) {
        MathExercise exercise = mathExerciseRepository.findByIdWithCategoriesAndCourseAndProblems(exerciseId).orElse(null);
        MathSubmission submission = mathSubmissionRepository.findByIdWithAnswersAndResults(submissionId).orElse(null);
        if (exercise == null || submission == null) {
            return;
        }
        GradingResult grading = mathGradingService.certifySubmission(exercise, submission);
        mathSubmissionRepository.save(submission);
        upsertResult(exercise, submission, grading, exerciseId);
        log.debug("Slow math certification of submission {}: {}", submissionId, grading.conclusive() ? grading.score() : "inconclusive");
    }

    private boolean hasCertifier(MathExercise exercise) {
        return exercise.getProblems() != null && exercise.getProblems().stream().anyMatch(problem -> problem.getCertifyingGraderType() != null);
    }

    /** Creates or updates the authoritative {@code AUTOMATIC} result for a conclusive verdict; an inconclusive verdict leaves the submission pending/for review. */
    private void upsertResult(MathExercise exercise, MathSubmission submission, GradingResult grading, long exerciseId) {
        if (!grading.conclusive()) {
            return;
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
        resultRepository.save(result);
    }
}
