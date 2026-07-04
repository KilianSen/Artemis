package de.tum.cit.aet.artemis.math.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.math.domain.GoalMode;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO.DerivationStepDTO;
import de.tum.cit.aet.artemis.math.grader.GraderType;

/**
 * Data Transfer Object for a single {@link MathProblem} inside a {@link de.tum.cit.aet.artemis.math.domain.MathExercise}.
 *
 * @param id                      the problem ID (null for new problems)
 * @param title                   the problem title
 * @param points                  the points this problem contributes to the exercise's aggregate score
 * @param sourceExpression        the starting expression for TRANSFORMATION mode
 * @param targetExpression        the goal expression students must derive in TRANSFORMATION mode
 * @param goalExpression          the goal tree for EQUATION mode; {@code null} in TRANSFORMATION mode
 * @param goalMode                how the goal is encoded: TRANSFORMATION (source→target) or EQUATION (single goal tree)
 * @param graderType              which {@link GraderType} backend grades this problem
 * @param partialCreditEnabled    whether distance-based partial credit is awarded when the target is not reached
 * @param acNormalization         whether the grader treats {@code +} and {@code ·} as commutative/associative
 * @param onlyShowApplicableRules whether the rule palette shows only rules applicable at the selected node
 * @param allowVerification       whether students may trigger math verification / hints for this problem
 * @param manualDerivation        true if students write the result expression themselves
 * @param exampleDerivations      the instructor-supplied worked derivation (an ordered list of steps)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MathProblemDTO(Long id, String title, Double points, MathNode sourceExpression, MathNode targetExpression, MathNode goalExpression, GoalMode goalMode,
        GraderType graderType, Boolean partialCreditEnabled, Boolean acNormalization, Boolean onlyShowApplicableRules, Boolean allowVerification, Boolean manualDerivation,
        List<DerivationStepDTO> exampleDerivations) {

    /**
     * @param problem the entity to project
     * @return a DTO carrying the problem's fields
     */
    public static MathProblemDTO of(MathProblem problem) {
        return new MathProblemDTO(problem.getId(), problem.getTitle(), problem.getPoints(), problem.getSourceExpression(), problem.getTargetExpression(),
                problem.getGoalExpression(), problem.getGoalMode(), problem.getGraderType(), problem.isPartialCreditEnabled(), problem.isAcNormalization(),
                problem.isOnlyShowApplicableRules(), problem.isAllowVerification(), problem.isManualDerivation(), problem.getExampleDerivations());
    }

    /**
     * @return a new {@link MathProblem} entity populated from this DTO (id is set only if non-null)
     */
    public MathProblem toEntity() {
        MathProblem problem = new MathProblem();
        if (id != null) {
            problem.setId(id);
        }
        problem.setTitle(title);
        problem.setPoints(points == null ? 0.0 : points);
        problem.setSourceExpression(sourceExpression);
        problem.setTargetExpression(targetExpression);
        problem.setGoalExpression(goalExpression);
        problem.setGoalMode(goalMode);
        problem.setGraderType(graderType);
        problem.setPartialCreditEnabled(Boolean.TRUE.equals(partialCreditEnabled));
        problem.setAcNormalization(Boolean.TRUE.equals(acNormalization));
        problem.setOnlyShowApplicableRules(Boolean.TRUE.equals(onlyShowApplicableRules));
        problem.setAllowVerification(allowVerification == null || allowVerification);
        problem.setManualDerivation(Boolean.TRUE.equals(manualDerivation));
        problem.setExampleDerivations(exampleDerivations);
        return problem;
    }
}
