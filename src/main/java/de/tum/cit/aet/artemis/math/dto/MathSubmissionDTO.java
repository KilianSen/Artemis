package de.tum.cit.aet.artemis.math.dto;

import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.math.domain.DerivationRole;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathGradingJobStatus;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathProblemAnswer;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.domain.StepDirection;
import de.tum.cit.aet.artemis.math.domain.StepKind;

/**
 * Data Transfer Object for {@link MathSubmission}.
 * Used as both request body (student sends {@code submitted}, {@code answers}) and response body.
 *
 * @param id             the submission ID (null for new submissions)
 * @param submitted      whether this is a final submission (triggers automatic grading)
 * @param submissionDate when the submission was last saved (response only)
 * @param results        automatic grading results (response only, populated after submit)
 * @param participation  the student's participation including exercise info (response only)
 * @param answers        the student's per-problem answers, each carrying its ordered derivation steps
 * @param gradingState   the current async grading state ({@code PENDING}/{@code REVIEW}/{@code FAILED}) for a remotely-graded
 *                           submission, or {@code null} when there is no async grading job (in-process grading or not submitted).
 *                           Lets the student editor show an "awaiting tutor review" state on reload without a live push.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MathSubmissionDTO(Long id, Boolean submitted, ZonedDateTime submissionDate, List<MathResultDTO> results, MathParticipationDTO participation,
        List<MathProblemAnswerDTO> answers, MathGradingJobStatus gradingState) {

    /**
     * One derivation step in the student's math.
     *
     * @param id               persisted step ID (null for new steps)
     * @param stepIndex        position in the derivation (0-based)
     * @param appliedRuleId    rule ID from the block registry
     * @param targetNodePath   index-path to the rewritten node within the current expression tree
     * @param resultExpression the full expression tree after this step
     * @param direction        direction in which the rule was applied; {@code null} defaults to {@link StepDirection#FORWARD}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record DerivationStepDTO(Long id, int stepIndex, String appliedRuleId, List<Integer> targetNodePath, MathNode resultExpression, StepDirection direction,
            DerivationRole derivationRole, StepKind kind, MathNode substitutionEquation) {

        /** Convenience constructor for callers that don't care about direction/role/kind (default FORWARD/MAIN/A). */
        public DerivationStepDTO(Long id, int stepIndex, String appliedRuleId, List<Integer> targetNodePath, MathNode resultExpression) {
            this(id, stepIndex, appliedRuleId, targetNodePath, resultExpression, StepDirection.FORWARD, DerivationRole.MAIN, StepKind.A, null);
        }

        /**
         * @param step the entity to project
         * @return a DTO mirroring the step
         */
        public static DerivationStepDTO of(DerivationStep step) {
            return new DerivationStepDTO(step.getId(), step.getStepIndex(), step.getAppliedRuleId(), step.getTargetNodePath(), step.getResultExpression(), step.getDirection(),
                    step.getDerivationRole(), step.getKind(), step.getSubstitutionEquation());
        }

        /**
         * @return a new {@link DerivationStep} entity populated from this DTO (id is set only if non-null)
         */
        public DerivationStep toEntity() {
            DerivationStep step = new DerivationStep();
            if (id != null) {
                step.setId(id);
            }
            step.setStepIndex(stepIndex);
            step.setAppliedRuleId(appliedRuleId);
            step.setTargetNodePath(targetNodePath);
            step.setResultExpression(resultExpression);
            step.setDirection(direction == null ? StepDirection.FORWARD : direction);
            step.setDerivationRole(derivationRole == null ? DerivationRole.MAIN : derivationRole);
            step.setKind(kind == null ? StepKind.A : kind);
            step.setSubstitutionEquation(substitutionEquation);
            return step;
        }
    }

    /**
     * A grading result. {@code rated} is load-bearing for the complaint flow, not decoration:
     * {@code ComplaintService.getIndividualComplaintDueDate} refuses to open a complaint window when
     * {@code !exercise.allowComplaintsForAutomaticAssessments && !result.rated}, and an absent field reads as
     * {@code undefined} — i.e. unrated — which disabled the Complain button for every math result.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record MathResultDTO(Long id, Double score, AssessmentType assessmentType, ZonedDateTime completionDate, Boolean rated, List<Feedback> feedbacks) {

        public static MathResultDTO of(Result result) {
            // Feedbacks are only projected when eagerly loaded (assessment paths); a lazy/uninitialized collection stays null.
            List<Feedback> feedbacks = Hibernate.isInitialized(result.getFeedbacks()) ? List.copyOf(result.getFeedbacks()) : null;
            return new MathResultDTO(result.getId(), result.getScore(), result.getAssessmentType(), result.getCompletionDate(), result.isRated(), feedbacks);
        }
    }

    /**
     * The participation's owner, projected to the fields the shared components identify a student by.
     * <p>
     * The flat {@code studentLogin}/{@code studentName} are kept for the assessment view, but they are not enough for
     * the complaint flow: {@code AccountService.isOwnerOfParticipation} <em>throws</em> "Participation does not have
     * any owners" unless {@code participation.student} (or a team) is present, and
     * {@code ComplaintsStudentViewComponent} compares {@code participation.student.id} against the logged-in user to
     * decide whether to offer the Complain button.
     *
     * @param id    the user id, compared against the logged-in user
     * @param login the user login, used by the owner check
     * @param name  the display name
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record MathStudentDTO(Long id, String login, String name) {

        /**
         * @param user the entity to project, may be {@code null}
         * @return the projection, or {@code null} when there is no user
         */
        public static MathStudentDTO of(User user) {
            return user == null ? null : new MathStudentDTO(user.getId(), user.getLogin(), user.getName());
        }
    }

    /**
     * A participation's submission reduced to its identity — deliberately without the back-reference to the
     * participation, which would make the response recursive.
     * <p>
     * {@code ComplaintsStudentViewComponent.ngOnInit} picks the newest submission out of
     * {@code participation.submissions} and then calls {@code findBySubmissionId(this.submission.id)}. Omitting the
     * collection left that undefined, so the lookup threw and aborted the rest of the hook — including the
     * {@code showSection} assignment that renders the complaint section at all.
     *
     * @param id             the submission id, used to look up an existing complaint
     * @param submissionDate when it was submitted
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record MathSubmissionStubDTO(Long id, ZonedDateTime submissionDate) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record MathParticipationDTO(Long id, MathExerciseDTO exercise, String studentLogin, String studentName, MathStudentDTO student,
            List<MathSubmissionStubDTO> submissions) {

        /**
         * Projects a {@link StudentParticipation} into a participation DTO, including the exercise stub when its
         * math-exercise association is initialized.
         *
         * @param participation the entity to project
         * @return the DTO carrying the user-facing participation fields
         */
        public static MathParticipationDTO of(StudentParticipation participation) {
            MathExerciseDTO exerciseDTO = null;
            if (Hibernate.isInitialized(participation.getExercise()) && participation.getExercise() instanceof MathExercise mathExercise
                    && Hibernate.isInitialized(mathExercise.getCategories())) {
                exerciseDTO = MathExerciseDTO.of(mathExercise);
            }
            String login = participation.getStudent().map(User::getLogin).orElse(null);
            String name = participation.getStudent().map(User::getName).orElse(null);
            MathStudentDTO student = participation.getStudent().map(MathStudentDTO::of).orElse(null);
            // Lazy on several paths; project only when fetched, and only the identity to keep the response acyclic.
            List<MathSubmissionStubDTO> submissions = Hibernate.isInitialized(participation.getSubmissions()) && participation.getSubmissions() != null
                    ? participation.getSubmissions().stream().map(s -> new MathSubmissionStubDTO(s.getId(), s.getSubmissionDate())).toList()
                    : List.of();
            return new MathParticipationDTO(participation.getId(), exerciseDTO, login, name, student, submissions);
        }
    }

    /**
     * Projects a {@link MathSubmission} into a DTO suitable for serialization back to the client.
     *
     * @param submission the entity to project
     * @return the DTO carrying the user-facing submission fields (results, participation, answers)
     */
    public static MathSubmissionDTO of(MathSubmission submission) {
        return of(submission, null);
    }

    /**
     * Projects a {@link MathSubmission} into a DTO, additionally carrying the current async grading state so the student
     * editor can show an "awaiting tutor review" state after a reload (when no live websocket push will arrive).
     *
     * @param submission   the entity to project
     * @param gradingState the current async grading-job status for the submission, or {@code null} if there is none
     * @return the DTO carrying the user-facing submission fields plus the grading state
     */
    public static MathSubmissionDTO of(MathSubmission submission, MathGradingJobStatus gradingState) {
        List<MathResultDTO> resultDTOs = null;
        List<Result> results = submission.getResults();
        if (results != null && !results.isEmpty()) {
            resultDTOs = results.stream().map(MathResultDTO::of).toList();
        }

        MathParticipationDTO participationDTO = null;
        if (submission.getParticipation() instanceof StudentParticipation sp) {
            participationDTO = MathParticipationDTO.of(sp);
        }

        List<MathProblemAnswerDTO> answerDTOs = null;
        Set<MathProblemAnswer> answers = submission.getAnswers();
        if (answers != null && !answers.isEmpty()) {
            answerDTOs = answers.stream().map(MathProblemAnswerDTO::of).toList();
        }

        return new MathSubmissionDTO(submission.getId(), submission.isSubmitted(), submission.getSubmissionDate(), resultDTOs, participationDTO, answerDTOs, gradingState);
    }

    /**
     * Builds a fresh {@link MathSubmission} entity from this DTO, wiring each answer's steps and back-reference.
     * The per-answer {@link MathProblemAnswer#getProblem() problem} association is left unset — the caller must resolve
     * it from the exercise by {@link MathProblemAnswerDTO#problemId()}.
     *
     * @return a new entity populated with the DTO's submission fields (id, submitted flag, answers if present)
     */
    public MathSubmission toEntity() {
        MathSubmission submission = new MathSubmission();
        if (id != null) {
            submission.setId(id);
        }
        submission.setSubmitted(Boolean.TRUE.equals(submitted));
        if (answers != null) {
            Set<MathProblemAnswer> answerEntities = new LinkedHashSet<>();
            for (MathProblemAnswerDTO answerDTO : answers) {
                MathProblemAnswer answer = answerDTO.toEntity();
                answer.setSubmission(submission);
                for (DerivationStep step : answer.getSteps()) {
                    step.setAnswer(answer);
                }
                answerEntities.add(answer);
            }
            submission.setAnswers(answerEntities);
        }
        return submission;
    }
}
