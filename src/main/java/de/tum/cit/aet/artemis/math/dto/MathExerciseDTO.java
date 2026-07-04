package de.tum.cit.aet.artemis.math.dto;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathProblem;

/**
 * Data Transfer Object for {@link MathExercise}.
 * Used for all exercise REST endpoints (create, update, get, list, import).
 * <p>
 * All per-problem configuration lives on the {@link MathProblemDTO}s in {@link #problems()}; the exercise itself carries
 * only the shared metadata.
 *
 * @param id                                     the exercise ID (null for create, set for update/response)
 * @param title                                  the exercise title
 * @param shortName                              the short name used for identification
 * @param problemStatement                       the problem description shown to students
 * @param description                            internal description / math instructions (math-specific)
 * @param exampleSolution                        example solution text
 * @param categories                             exercise categories as JSON-encoded strings
 * @param difficulty                             the difficulty level
 * @param maxPoints                              maximum achievable points (computed as the sum of the problems' points)
 * @param bonusPoints                            additional bonus points
 * @param includedInOverallScore                 how this exercise counts toward the course grade
 * @param allowComplaintsForAutomaticAssessments whether complaints are allowed
 * @param allowFeedbackRequests                  whether feedback requests are enabled
 * @param presentationScoreEnabled               whether presentation scores are tracked
 * @param secondCorrectionEnabled                whether a second correction round is enabled
 * @param feedbackSuggestionModule               the AI feedback suggestion module identifier
 * @param gradingInstructions                    free-text grading instructions for tutors
 * @param releaseDate                            when the exercise becomes visible to students
 * @param startDate                              when students can start working
 * @param dueDate                                submission deadline
 * @param assessmentDueDate                      deadline for tutors to complete assessments
 * @param exampleSolutionPublicationDate         when the example solution becomes visible
 * @param courseId                               the course ID (math exercises are course-only)
 * @param problems                               the ordered list of math problems (questions) this exercise holds
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MathExerciseDTO(Long id, String title, String shortName, String problemStatement, String description, String exampleSolution, Set<String> categories,
        DifficultyLevel difficulty, Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments,
        Boolean allowFeedbackRequests, Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule, String gradingInstructions,
        ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, Long courseId,
        List<MathProblemDTO> problems) {

    /**
     * @param exercise the entity to project
     * @return a DTO carrying the entity's user-facing fields
     */
    public static MathExerciseDTO of(MathExercise exercise) {
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        // The problems collection is lazy; only project it when it was fetched (e.g. list/search endpoints omit it).
        boolean problemsLoaded = Hibernate.isInitialized(exercise.getProblems()) && exercise.getProblems() != null;
        List<MathProblemDTO> problemDTOs = problemsLoaded ? exercise.getProblems().stream().map(MathProblemDTO::of).toList() : List.of();
        double maxPoints = problemsLoaded ? computeMaxPoints(exercise) : (exercise.getMaxPoints() == null ? 0.0 : exercise.getMaxPoints());
        return new MathExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getProblemStatement(), exercise.getDescription(),
                exercise.getExampleSolution(), exercise.getCategories(), exercise.getDifficulty(), maxPoints, exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), courseId, problemDTOs);
    }

    /** Sum of the exercise's problem points; falls back to the entity's own maxPoints if it has no problems yet. */
    private static double computeMaxPoints(MathExercise exercise) {
        if (exercise.getProblems() == null || exercise.getProblems().isEmpty()) {
            return exercise.getMaxPoints() == null ? 0.0 : exercise.getMaxPoints();
        }
        return exercise.getProblems().stream().mapToDouble(MathProblem::getPoints).sum();
    }

    /**
     * Applies the fields of this DTO to an existing {@link MathExercise} entity, replacing its problem list.
     * The Course association is not applied here — the caller must set it from the database.
     *
     * @param exercise the entity to populate
     */
    public void applyToEntity(MathExercise exercise) {
        exercise.setTitle(title);
        exercise.setShortName(shortName);
        exercise.setProblemStatement(problemStatement);
        exercise.setDescription(description);
        exercise.setExampleSolution(exampleSolution);
        exercise.setCategories(categories);
        exercise.setDifficulty(difficulty);
        exercise.setBonusPoints(bonusPoints);
        exercise.setIncludedInOverallScore(includedInOverallScore);
        exercise.setGradingInstructions(gradingInstructions);
        exercise.setReleaseDate(releaseDate);
        exercise.setStartDate(startDate);
        exercise.setDueDate(dueDate);
        exercise.setAssessmentDueDate(assessmentDueDate);
        exercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        exercise.setAllowFeedbackRequests(Boolean.TRUE.equals(allowFeedbackRequests));
        exercise.setAllowComplaintsForAutomaticAssessments(Boolean.TRUE.equals(allowComplaintsForAutomaticAssessments));
        exercise.setPresentationScoreEnabled(presentationScoreEnabled);
        exercise.setSecondCorrectionEnabled(Boolean.TRUE.equals(secondCorrectionEnabled));
        if (feedbackSuggestionModule != null) {
            exercise.setFeedbackSuggestionModule(feedbackSuggestionModule);
        }
        List<MathProblem> problemEntities = new ArrayList<>();
        if (problems != null) {
            for (MathProblemDTO problemDTO : problems) {
                problemEntities.add(problemDTO.toEntity());
            }
        }
        exercise.setProblems(problemEntities);
        // The exercise's maxPoints is the sum of its problems' points so the shared scoring machinery keeps working.
        exercise.setMaxPoints(problemEntities.stream().mapToDouble(MathProblem::getPoints).sum());
    }
}
