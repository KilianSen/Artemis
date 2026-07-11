package de.tum.cit.aet.artemis.math.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.hibernate.Hibernate;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseImportService;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.domain.MathProblemAnswer;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;

@Conditional(MathEnabled.class)
@Lazy
@Service
public class MathExerciseImportService extends ExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(MathExerciseImportService.class);

    private final MathExerciseRepository mathExerciseRepository;

    private final MathSubmissionRepository mathSubmissionRepository;

    private final ChannelService channelService;

    public MathExerciseImportService(MathExerciseRepository mathExerciseRepository, MathSubmissionRepository mathSubmissionRepository,
            ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository, ChannelService channelService,
            FeedbackService feedbackService) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, feedbackService);
        this.mathExerciseRepository = mathExerciseRepository;
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.channelService = channelService;
    }

    /**
     * Imports a math exercise creating a new entity, copying all basic values and saving it in the database.
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @NonNull
    public MathExercise importMathExercise(final MathExercise templateExercise, MathExercise importedExercise) {
        log.debug("Creating a new Exercise based on exercise {}", templateExercise);
        Map<Long, GradingInstruction> gradingInstructionCopyTracker = new HashMap<>();
        MathExercise newExercise = copyMathExerciseBasis(importedExercise, gradingInstructionCopyTracker);

        MathExercise savedExercise = mathExerciseRepository.save(newExercise);

        channelService.createExerciseChannel(savedExercise, Optional.ofNullable(importedExercise.getChannelName()));
        Map<Long, MathProblem> problemMapping = buildProblemMapping(templateExercise, savedExercise);
        savedExercise.setExampleSubmissions(copyExampleSubmission(templateExercise, savedExercise, gradingInstructionCopyTracker, problemMapping));

        return savedExercise;
    }

    /**
     * Maps each template problem id to the corresponding freshly-copied problem on the new exercise, matched by position.
     * Lets example-submission answers be re-pointed from the template's problems to the new exercise's problems.
     *
     * @param templateExercise the exercise being imported from
     * @param savedExercise    the newly persisted exercise with its copied problems
     * @return a mapping from template problem id to the new problem
     */
    private Map<Long, MathProblem> buildProblemMapping(MathExercise templateExercise, MathExercise savedExercise) {
        Map<Long, MathProblem> mapping = new HashMap<>();
        List<MathProblem> templateProblems = mathExerciseRepository.findByIdWithCategoriesAndProblems(templateExercise.getId()).map(MathExercise::getProblems).orElse(List.of());
        List<MathProblem> newProblems = savedExercise.getProblems() == null ? List.of() : savedExercise.getProblems();
        for (int i = 0; i < templateProblems.size() && i < newProblems.size(); i++) {
            if (templateProblems.get(i).getId() != null) {
                mapping.put(templateProblems.get(i).getId(), newProblems.get(i));
            }
        }
        return mapping;
    }

    /**
     * This helper method copies all attributes of the {@code importedExercise} into the new exercise.
     *
     * @param importedExercise              The exercise from which to copy the basis
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return the cloned MathExercise basis
     */
    @NonNull
    private MathExercise copyMathExerciseBasis(MathExercise importedExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        MathExercise newExercise = new MathExercise();

        super.copyExerciseBasis(newExercise, importedExercise, gradingInstructionCopyTracker);
        // Deep-copy each problem so the new exercise owns its own problem rows (cascade/orphanRemoval on MathExercise#problems).
        if (importedExercise.getProblems() != null) {
            for (MathProblem originalProblem : importedExercise.getProblems()) {
                newExercise.addProblem(copyProblem(originalProblem));
            }
        }
        return newExercise;
    }

    /**
     * Creates a detached deep copy of a single problem's configuration (without its id or exercise back-reference).
     *
     * @param originalProblem the problem to copy
     * @return the copied problem
     */
    private MathProblem copyProblem(MathProblem originalProblem) {
        MathProblem copy = new MathProblem();
        copy.setTitle(originalProblem.getTitle());
        copy.setPoints(originalProblem.getPoints());
        copy.setSourceExpression(originalProblem.getSourceExpression());
        copy.setTargetExpression(originalProblem.getTargetExpression());
        copy.setGoalExpression(originalProblem.getGoalExpression());
        copy.setGoalMode(originalProblem.getGoalMode());
        copy.setGraderType(originalProblem.getGraderType());
        copy.setManualDerivation(originalProblem.isManualDerivation());
        copy.setAllowVerification(originalProblem.isAllowVerification());
        copy.setOnlyShowApplicableRules(originalProblem.isOnlyShowApplicableRules());
        copy.setAcNormalization(originalProblem.isAcNormalization());
        copy.setPartialCreditEnabled(originalProblem.isPartialCreditEnabled());
        copy.setExampleDerivations(originalProblem.getExampleDerivations());
        return copy;
    }

    /**
     * This functions does a hard copy of the example submissions contained in {@code templateExercise}.
     * To copy the corresponding {@link de.tum.cit.aet.artemis.exercise.domain.Submission} entity this calls {@link #copySubmission(Submission, Map)}.
     *
     * @param templateExercise              The original exercise from which to fetch the example submissions
     * @param newExercise                   The new exercise in which we will insert the example submissions
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return The cloned set of example submissions
     */
    private Set<ExampleSubmission> copyExampleSubmission(Exercise templateExercise, Exercise newExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker,
            Map<Long, MathProblem> problemMapping) {
        log.debug("Copying the ExampleSubmissions to new Exercise: {}", newExercise);
        Set<ExampleSubmission> newExampleSubmissions = new HashSet<>();
        if (!Hibernate.isInitialized(templateExercise.getExampleSubmissions())) {
            return newExampleSubmissions;
        }
        for (ExampleSubmission originalExampleSubmission : templateExercise.getExampleSubmissions()) {
            // Hard-copy the submission: ExampleSubmission.submission is a unique @OneToOne with cascade=REMOVE/orphanRemoval,
            // so the new example submission must own its own submission rather than share the template's.
            MathSubmission newSubmission = copySubmission(originalExampleSubmission.getSubmission(), gradingInstructionCopyTracker, problemMapping);

            ExampleSubmission newExampleSubmission = new ExampleSubmission();
            newExampleSubmission.setExercise(newExercise);
            newExampleSubmission.setSubmission(newSubmission);
            newExampleSubmission.setAssessmentExplanation(originalExampleSubmission.getAssessmentExplanation());

            exampleSubmissionRepository.save(newExampleSubmission);
            newExampleSubmissions.add(newExampleSubmission);
        }
        return newExampleSubmissions;
    }

    /**
     * This helper function does a hard copy of the {@code originalSubmission} into a new {@link MathSubmission}, including its
     * latest example result. Mirrors {@code TextExerciseImportService#copySubmission} but without text blocks.
     *
     * @param originalSubmission            The original submission to be copied (may be {@code null})
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return The cloned submission
     */
    private MathSubmission copySubmission(final Submission originalSubmission, Map<Long, GradingInstruction> gradingInstructionCopyTracker, Map<Long, MathProblem> problemMapping) {
        MathSubmission newSubmission = new MathSubmission();
        if (originalSubmission != null) {
            log.debug("Copying the Submission to new ExampleSubmission: {}", newSubmission);
            newSubmission.setExampleSubmission(true);
            newSubmission.setSubmissionDate(originalSubmission.getSubmissionDate());
            newSubmission.setType(originalSubmission.getType());
            newSubmission.setParticipation(originalSubmission.getParticipation());
            // Deep-copy the per-problem answers and their derivation steps (loaded lazily via a targeted query) so the example
            // submission owns its own answer/step rows rather than sharing the template's (cascade/orphanRemoval on MathSubmission#answers).
            List<MathProblemAnswer> originalAnswers = mathSubmissionRepository.findByIdWithAnswersAndResults(originalSubmission.getId()).map(MathSubmission::getAnswers)
                    .orElse(List.of());
            for (MathProblemAnswer originalAnswer : originalAnswers) {
                MathProblemAnswer copiedAnswer = new MathProblemAnswer();
                copiedAnswer.setScoreInPoints(originalAnswer.getScoreInPoints());
                if (originalAnswer.getProblem() != null) {
                    copiedAnswer.setProblem(problemMapping.get(originalAnswer.getProblem().getId()));
                }
                copiedAnswer.setSubmission(newSubmission);
                for (DerivationStep originalStep : originalAnswer.getSteps()) {
                    DerivationStep copiedStep = new DerivationStep();
                    copiedStep.setStepIndex(originalStep.getStepIndex());
                    copiedStep.setAppliedRuleId(originalStep.getAppliedRuleId());
                    copiedStep.setTargetNodePath(originalStep.getTargetNodePath());
                    copiedStep.setResultExpression(originalStep.getResultExpression());
                    copiedStep.setDirection(originalStep.getDirection());
                    copiedStep.setAnswer(copiedAnswer);
                    copiedAnswer.getSteps().add(copiedStep);
                }
                newSubmission.getAnswers().add(copiedAnswer);
            }
            newSubmission = submissionRepository.saveAndFlush(newSubmission);
            // Load the assessment graph (result + feedbacks + assessor) in a separate targeted query instead of eagerly fetching
            // it on the exercise-import query, which would grow that query's fetch graph beyond the allowed size.
            Result originalResult = mathSubmissionRepository.findByIdWithResultsAndFeedbacksAndAssessor(originalSubmission.getId()).map(MathSubmission::getLatestResult)
                    .orElse(null);
            if (originalResult != null) {
                newSubmission.addResult(copyExampleResult(originalResult, newSubmission, gradingInstructionCopyTracker));
                newSubmission = submissionRepository.saveAndFlush(newSubmission);
            }
        }
        return newSubmission;
    }
}
