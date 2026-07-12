package de.tum.cit.aet.artemis.math.web;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathGradingJob;
import de.tum.cit.aet.artemis.math.domain.MathGradingJobStatus;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.domain.MathProblemAnswer;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.HintRequestDTO;
import de.tum.cit.aet.artemis.math.dto.HintSuggestionDTO;
import de.tum.cit.aet.artemis.math.dto.ManualResultRequestDTO;
import de.tum.cit.aet.artemis.math.dto.MathProblemAnswerDTO;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO.DerivationStepDTO;
import de.tum.cit.aet.artemis.math.grader.GradingResult;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.repository.MathGradingJobRepository;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;
import de.tum.cit.aet.artemis.math.service.MathAssessmentService;
import de.tum.cit.aet.artemis.math.service.MathGradingDispatcher;
import de.tum.cit.aet.artemis.math.service.MathGradingService;
import de.tum.cit.aet.artemis.math.service.MathSubmissionService;

@Lazy
@Conditional(MathEnabled.class)
@RestController
@RequestMapping("api/math/")
public class MathSubmissionResource {

    private static final Logger log = LoggerFactory.getLogger(MathSubmissionResource.class);

    private final MathSubmissionRepository mathSubmissionRepository;

    private final MathExerciseRepository mathExerciseRepository;

    private final ResultRepository resultRepository;

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authCheckService;

    private final MathSubmissionService mathSubmissionService;

    private final MathGradingService mathGradingService;

    private final MathGradingDispatcher mathGradingDispatcher;

    private final MathGradingJobRepository mathGradingJobRepository;

    private final MathAssessmentService mathAssessmentService;

    public MathSubmissionResource(MathSubmissionRepository mathSubmissionRepository, MathExerciseRepository mathExerciseRepository, ResultRepository resultRepository,
            UserRepository userRepository, StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authCheckService,
            MathSubmissionService mathSubmissionService, MathGradingService mathGradingService, MathGradingDispatcher mathGradingDispatcher,
            MathGradingJobRepository mathGradingJobRepository, MathAssessmentService mathAssessmentService) {
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.mathExerciseRepository = mathExerciseRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authCheckService = authCheckService;
        this.mathSubmissionService = mathSubmissionService;
        this.mathGradingService = mathGradingService;
        this.mathGradingDispatcher = mathGradingDispatcher;
        this.mathGradingJobRepository = mathGradingJobRepository;
        this.mathAssessmentService = mathAssessmentService;
    }

    /** The current async grading state for a submission (latest job status), or {@code null} when there is no grading job. */
    private MathGradingJobStatus gradingStateFor(MathSubmission submission) {
        if (submission == null || submission.getId() == null) {
            return null;
        }
        return mathGradingJobRepository.findFirstBySubmissionIdOrderByIdDesc(submission.getId()).map(MathGradingJob::getStatus).orElse(null);
    }

    /** Whether any problem of the exercise is graded by a remote (Regate) backend — those are graded asynchronously. */
    private boolean usesRemoteGrader(MathExercise exercise) {
        return exercise.getProblems() != null && exercise.getProblems().stream().anyMatch(problem -> problem.getGraderType() != null && problem.getGraderType().isRemote());
    }

    @PostMapping("exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> createMathSubmission(@PathVariable Long exerciseId, @RequestBody MathSubmissionDTO mathSubmissionDTO) {
        log.debug("REST request to save MathSubmission for exercise : {}", exerciseId);
        return ResponseEntity.ok(saveAndEvaluate(exerciseId, mathSubmissionDTO));
    }

    @PutMapping("exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> updateMathSubmission(@PathVariable Long exerciseId, @RequestBody MathSubmissionDTO mathSubmissionDTO) {
        log.debug("REST request to update MathSubmission for exercise : {}", exerciseId);
        return ResponseEntity.ok(saveAndEvaluate(exerciseId, mathSubmissionDTO));
    }

    private MathSubmissionDTO saveAndEvaluate(Long exerciseId, MathSubmissionDTO dto) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        MathExercise mathExercise = mathExerciseRepository.findByIdWithCategoriesAndProblems(exerciseId).orElseThrow();
        // Re-check current course membership: a StudentParticipation persists after un-enrollment, so its existence alone is not sufficient authorization.
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, mathExercise, user);

        MathSubmission submission;
        if (dto.id() != null) {
            // Updating an existing submission: load it (with participation) so the ownership check below has data and only answers/submitted change.
            submission = mathSubmissionRepository.findByIdWithAnswersResultsAndParticipation(dto.id()).orElseThrow();
            submission.setSubmitted(Boolean.TRUE.equals(dto.submitted()));
            // Replace the per-problem answers with the incoming ones (orphanRemoval deletes the old answer/step rows).
            submission.getAnswers().clear();
        }
        else {
            submission = new MathSubmission();
            submission.setSubmitted(Boolean.TRUE.equals(dto.submitted()));
        }
        // Build one answer per submitted answer, resolving its problem from the exercise, then wildcard-validate + normalize each step.
        if (dto.answers() != null) {
            for (MathProblemAnswerDTO answerDTO : dto.answers()) {
                MathProblem problem = findProblemOrThrow(mathExercise, answerDTO.problemId());
                MathProblemAnswer answer = new MathProblemAnswer();
                if (answerDTO.id() != null) {
                    answer.setId(answerDTO.id());
                }
                answer.setProblem(problem);
                answer.setSubmission(submission);
                if (answerDTO.steps() != null) {
                    for (DerivationStepDTO stepDTO : answerDTO.steps()) {
                        DerivationStep step = stepDTO.toEntity();
                        try {
                            MathNodes.assertWildcardFree(step.getResultExpression());
                        }
                        catch (IllegalArgumentException e) {
                            throw new BadRequestAlertException(e.getMessage(), "mathSubmission", "wildcardNotAllowed");
                        }
                        step.setResultExpression(MathNodes.normalize(step.getResultExpression()));
                        step.setAnswer(answer);
                        answer.getSteps().add(step);
                    }
                }
                submission.getAnswers().add(answer);
            }
        }
        // Re-check course membership and, for updates, verify the submission belongs to the current user (prevents injecting into another student's submission).
        mathSubmissionService.checkSubmissionAllowanceElseThrow(mathExercise, submission, user);
        // Enforce the due date and apply the standard non-programming submission lifecycle (submission date, MANUAL type, FINISHED participation, no injected results).
        MathSubmission saved = mathSubmissionService.handleMathSubmission(submission, mathExercise, user);

        // Auto-grade on submit: handleMathSubmission already stripped any client-injected result; attach the authoritative AUTOMATIC result.
        if (Boolean.TRUE.equals(saved.isSubmitted())) {
            if (usesRemoteGrader(mathExercise)) {
                // A remote backend may take seconds; grade off the request thread so the submit returns immediately.
                // No result is attached yet — a durable grading job is enqueued and the authoritative result is pushed
                // to the client over the result websocket once it lands.
                saved = mathSubmissionRepository.save(saved);
                mathGradingDispatcher.dispatch(exerciseId, saved.getId());
            }
            else {
                // In-process grading is fast: grade synchronously and attach the authoritative AUTOMATIC result. The
                // aggregate grader also records each answer's earned points on the answer entity; persist them.
                GradingResult grading = mathGradingService.gradeSubmission(mathExercise, saved);
                saved = mathSubmissionRepository.save(saved);
                // Only attach a result for a conclusive verdict; an inconclusive one leaves the submission for manual review.
                if (grading.conclusive()) {
                    Result result = new Result();
                    result.setSubmission(saved);
                    result.setAssessmentType(AssessmentType.AUTOMATIC);
                    result.setCompletionDate(ZonedDateTime.now());
                    result.setRated(true);
                    result.setExerciseId(exerciseId);
                    result.setScore(grading.score(), mathExercise.getCourseViaExerciseGroupOrCourseMember());
                    resultRepository.save(result);
                    saved.addResult(result);
                }
            }
        }

        StudentParticipation participation = (StudentParticipation) saved.getParticipation();
        if (participation != null) {
            // attach the categories-loaded exercise so the DTO can project it without a LazyInitializationException
            participation.setExercise(mathExercise);
        }
        // Strip solution/grading data before returning the exercise to a student (nulls example solution when unpublished).
        mathExercise.filterSensitiveInformation();
        return MathSubmissionDTO.of(saved, gradingStateFor(saved));
    }

    private MathProblem findProblemOrThrow(MathExercise exercise, Long problemId) {
        if (problemId == null) {
            throw new BadRequestAlertException("Each answer must reference a problem", "mathSubmission", "problemRequired");
        }
        return exercise.getProblems().stream().filter(problem -> problemId.equals(problem.getId())).findFirst()
                .orElseThrow(() -> new BadRequestAlertException("Answer references a problem not in the exercise", "mathSubmission", "problemNotFound"));
    }

    /**
     * GET /participations/:participationId/math-editor : Returns the data needed for the math editor,
     * including the participation, the latest MathSubmission, and results.
     *
     * @param participationId the participation for which to load editor data
     * @return ResponseEntity with the latest MathSubmission (empty if no submission yet)
     */
    @GetMapping("participations/{participationId}/math-editor")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> getDataForMathEditor(@PathVariable Long participationId) {
        log.debug("REST request to get math editor data for participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithLatestSubmissionsResultsFeedbackElseThrow(participationId);

        if (!(participation.getExercise() instanceof MathExercise mathExercise)) {
            throw new IllegalArgumentException("Participation does not belong to a math exercise");
        }
        // Reload with categories and problems to avoid LazyInitializationException when DTO is serialized/logged
        mathExercise = mathExerciseRepository.findByIdWithCategoriesAndProblems(mathExercise.getId()).orElseThrow();
        participation.setExercise(mathExercise);
        boolean isOwner = authCheckService.isOwnerOfParticipation(participation);
        if (!(isOwner || authCheckService.isAtLeastTeachingAssistantForExercise(mathExercise))) {
            throw new AccessForbiddenException("participation", participationId);
        }
        if (isOwner) {
            // A StudentParticipation persists after un-enrollment, so ownership alone is not sufficient: require current STUDENT membership.
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, mathExercise, null);
        }

        Optional<MathSubmission> latestSubmission = participation.findLatestSubmission().filter(s -> s instanceof MathSubmission).map(s -> (MathSubmission) s);

        MathSubmission submission;
        submission = latestSubmission.map(mathSubmission -> mathSubmissionRepository.findByIdWithAnswersAndResults(mathSubmission.getId()).orElseThrow())
                .orElseGet(MathSubmission::new);
        submission.setParticipation(participation);
        // Strip solution/grading data before returning the exercise through the editor DTO (nulls example solution when unpublished).
        mathExercise.filterSensitiveInformation();
        return ResponseEntity.ok(MathSubmissionDTO.of(submission, gradingStateFor(submission)));
    }

    /**
     * GET /math-submissions/{submissionId} : get a single submission; restricted to its owner or a tutor of the exercise.
     *
     * @param submissionId the id of the submission to retrieve
     * @return the submission
     */
    @GetMapping("math-submissions/{submissionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> getMathSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get MathSubmission : {}", submissionId);
        MathSubmission submission = mathSubmissionRepository.findByIdWithAnswersResultsAndParticipation(submissionId).orElseThrow();
        if (!(submission.getParticipation() instanceof StudentParticipation participation) || !(participation.getExercise() instanceof MathExercise mathExercise)) {
            throw new AccessForbiddenException("mathSubmission", submissionId);
        }
        boolean isOwner = authCheckService.isOwnerOfParticipation(participation);
        if (!(isOwner || authCheckService.isAtLeastTeachingAssistantForExercise(mathExercise))) {
            throw new AccessForbiddenException("mathSubmission", submissionId);
        }
        if (isOwner) {
            // A StudentParticipation persists after un-enrollment, so ownership alone is not sufficient: require current STUDENT membership.
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, mathExercise, null);
        }
        // Defensive: the current query does not fetch categories (so the DTO omits the exercise), but filter anyway so this
        // student-facing endpoint never leaks solution/grading data if the exercise is ever projected here.
        mathExercise.filterSensitiveInformation();
        return ResponseEntity.ok(MathSubmissionDTO.of(submission, gradingStateFor(submission)));
    }

    /**
     * GET /math-submissions/{submissionId}/for-assessment : load a submission for tutor assessment,
     * eagerly fetching steps, results, and participation.
     *
     * @param submissionId the submission to load
     * @return the submission populated for the assessment view
     */
    @GetMapping("math-submissions/{submissionId}/for-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<MathSubmissionDTO> getMathSubmissionForAssessment(@PathVariable Long submissionId) {
        log.debug("REST request to get MathSubmission for assessment : {}", submissionId);
        MathSubmission submission = mathSubmissionRepository.findByIdWithAnswersResultsAndParticipation(submissionId).orElseThrow();
        if (!(submission.getParticipation() != null && submission.getParticipation().getExercise() instanceof MathExercise pe)) {
            throw new AccessForbiddenException("mathSubmission", submissionId);
        }
        MathExercise exerciseWithCategories = mathExerciseRepository.findByIdWithCategoriesAndCourseAndProblems(pe.getId()).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exerciseWithCategories, null);
        submission.getParticipation().setExercise(exerciseWithCategories);
        loadResultFeedbacks(submission);
        return ResponseEntity.ok(MathSubmissionDTO.of(submission));
    }

    /**
     * GET /exercises/{exerciseId}/math-submissions : list submitted submissions for an exercise. By default returns all
     * submitted submissions (instructor overview); with {@code assessedByTutor=true} returns only the submissions the
     * current tutor has a manual assessment on (the assessment dashboard's "assessed by me" list).
     *
     * @param exerciseId      the exercise whose submissions to list
     * @param assessedByTutor when true, restrict to submissions this tutor manually assessed
     * @return the matching submissions
     */
    @GetMapping("exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastTutor
    public ResponseEntity<List<MathSubmissionDTO>> getSubmittedMathSubmissions(@PathVariable Long exerciseId,
            @RequestParam(value = "assessedByTutor", defaultValue = "false") boolean assessedByTutor) {
        log.debug("REST request to get submitted MathSubmissions for exercise {} (assessedByTutor={})", exerciseId, assessedByTutor);
        MathExercise exercise = mathExerciseRepository.findByIdWithCategories(exerciseId).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        List<MathSubmission> submissions;
        if (assessedByTutor) {
            User tutor = userRepository.getUserWithGroupsAndAuthorities();
            submissions = mathSubmissionRepository.findAssessedByTutor(exerciseId, tutor.getId());
        }
        else {
            submissions = mathSubmissionRepository.findSubmittedByExerciseId(exerciseId);
        }
        return ResponseEntity.ok(submissions.stream().map(MathSubmissionDTO::of).toList());
    }

    /**
     * GET /exercises/{exerciseId}/math-submission-without-assessment : the next submission eligible for a new manual
     * assessment (submitted but auto-grading was inconclusive/failed, so it carries no result). With {@code lock=true}
     * the submission is soft-locked to the current tutor so no one else is offered it.
     *
     * @param exerciseId the exercise to pull an assessable submission from
     * @param lock       whether to lock the returned submission to the current tutor
     * @return the next assessable submission, or 200 with an empty body if none remain
     */
    @GetMapping("exercises/{exerciseId}/math-submission-without-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<MathSubmissionDTO> getMathSubmissionWithoutAssessment(@PathVariable Long exerciseId, @RequestParam(value = "lock", defaultValue = "false") boolean lock) {
        log.debug("REST request to get a math submission without assessment for exercise {} (lock={})", exerciseId, lock);
        MathExercise exercise = mathExerciseRepository.findByIdWithCategoriesAndCourseAndProblems(exerciseId).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        User tutor = userRepository.getUserWithGroupsAndAuthorities();
        Optional<MathSubmission> assessable = lock ? mathAssessmentService.lockAndGetAssessableSubmission(exerciseId, tutor)
                : mathAssessmentService.getAssessableSubmission(exerciseId);
        if (assessable.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        MathSubmission submission = mathSubmissionRepository.findByIdWithAnswersResultsAndParticipation(assessable.get().getId()).orElseThrow();
        if (submission.getParticipation() != null) {
            submission.getParticipation().setExercise(exercise);
        }
        loadResultFeedbacks(submission);
        return ResponseEntity.ok(MathSubmissionDTO.of(submission));
    }

    /**
     * POST /exercises/{exerciseId}/problems/{problemId}/hints : ranked next-step suggestions for the student's current
     * math state on a single problem. Gated by {@link MathProblem#isAllowVerification()} — instructors can disable hints
     * per problem.
     *
     * @param exerciseId the exercise the student is working on
     * @param problemId  the problem the student is working on
     * @param request    the hint request body carrying the current expression
     * @return up to three {@link HintSuggestionDTO}s ranked by progress toward the goal
     */
    @PostMapping("exercises/{exerciseId}/problems/{problemId}/hints")
    @EnforceAtLeastStudent
    public ResponseEntity<List<HintSuggestionDTO>> suggestHints(@PathVariable Long exerciseId, @PathVariable Long problemId, @RequestBody HintRequestDTO request) {
        log.debug("REST request to compute hints for math exercise {} problem {}", exerciseId, problemId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        MathExercise exercise = mathExerciseRepository.findByIdWithCategoriesAndProblems(exerciseId).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        MathProblem problem = findProblemOrThrow(exercise, problemId);
        if (!problem.isAllowVerification()) {
            throw new AccessForbiddenException("Hint generation is disabled for this problem.");
        }
        try {
            MathNodes.assertWildcardFree(request.currentExpression());
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), "mathSubmission", "wildcardNotAllowed");
        }
        List<HintSuggestionDTO> hints = mathGradingService.suggestHints(problem, MathNodes.normalize(request.currentExpression())).stream().map(HintSuggestionDTO::of).toList();
        return ResponseEntity.ok(hints);
    }

    /**
     * PUT /math-submissions/{submissionId}/manual-result : record the tutor's manual score + feedback. With
     * {@code submit=false} (default) this saves a draft that keeps the submission locked and hidden from the student;
     * {@code submit=true} finalizes the assessment.
     *
     * @param submissionId the submission to assess
     * @param submit       whether to finalize the assessment or save a draft
     * @param request      the manual score in [0, 100] plus optional unreferenced feedback
     * @return the submission with the manual result attached
     */
    @PutMapping("math-submissions/{submissionId}/manual-result")
    @EnforceAtLeastTutor
    public ResponseEntity<MathSubmissionDTO> saveManualResult(@PathVariable Long submissionId, @RequestParam(value = "submit", defaultValue = "false") boolean submit,
            @RequestBody ManualResultRequestDTO request) {
        log.debug("REST request to save manual result for MathSubmission {} (submit={})", submissionId, submit);
        MathSubmission submission = mathSubmissionRepository.findByIdWithAnswersResultsAndParticipation(submissionId).orElseThrow();
        if (!(submission.getParticipation() != null && submission.getParticipation().getExercise() instanceof MathExercise pe)) {
            throw new AccessForbiddenException("mathSubmission", submissionId);
        }
        MathExercise exercise = mathExerciseRepository.findByIdWithCategoriesAndCourseAndProblems(pe.getId()).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        User tutor = userRepository.getUserWithGroupsAndAuthorities();

        mathAssessmentService.saveManualAssessment(submission, exercise, request.score(), request.feedbacks(), submit, tutor);

        submission = mathSubmissionRepository.findByIdWithAnswersResultsAndParticipation(submissionId).orElseThrow();
        if (submission.getParticipation() != null) {
            submission.getParticipation().setExercise(exercise);
        }
        loadResultFeedbacks(submission);
        return ResponseEntity.ok(MathSubmissionDTO.of(submission));
    }

    /**
     * PUT /math-submissions/{submissionId}/cancel-assessment : cancel an in-progress assessment, releasing the soft lock
     * (deletes the tutor's draft result). Only the assessor or an instructor may cancel.
     *
     * @param submissionId the submission whose assessment to cancel
     * @return 200 once the lock is released
     */
    @PutMapping("math-submissions/{submissionId}/cancel-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> cancelAssessment(@PathVariable Long submissionId) {
        log.debug("REST request to cancel assessment of MathSubmission {}", submissionId);
        MathSubmission submission = mathSubmissionRepository.findByIdWithAnswersResultsAndParticipation(submissionId).orElseThrow();
        if (!(submission.getParticipation() != null && submission.getParticipation().getExercise() instanceof MathExercise pe)) {
            throw new AccessForbiddenException("mathSubmission", submissionId);
        }
        MathExercise exercise = mathExerciseRepository.findByIdWithCategoriesAndCourseAndProblems(pe.getId()).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        // Only the assessor who holds the lock (or an instructor) may cancel it.
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Result latest = submission.getLatestResult();
        boolean isAssessor = latest != null && latest.getAssessor() != null && latest.getAssessor().getId().equals(user.getId());
        if (!isAssessor && !authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            throw new AccessForbiddenException("You are not allowed to cancel this assessment.");
        }
        mathAssessmentService.cancelAssessment(submission);
        return ResponseEntity.ok().build();
    }

    /**
     * Eagerly loads each result's feedbacks so they serialize into {@link MathSubmissionDTO} — the base submission
     * query fetches results lazily without feedbacks, which would otherwise be dropped (or fail on a closed session).
     */
    private void loadResultFeedbacks(MathSubmission submission) {
        if (submission.getResults() == null) {
            return;
        }
        for (Result result : submission.getResults()) {
            resultRepository.findByIdWithEagerFeedbacks(result.getId()).ifPresent(loaded -> result.setFeedbacks(loaded.getFeedbacks()));
        }
    }
}
