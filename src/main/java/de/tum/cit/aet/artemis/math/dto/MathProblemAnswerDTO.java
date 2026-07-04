package de.tum.cit.aet.artemis.math.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathProblemAnswer;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO.DerivationStepDTO;

/**
 * Data Transfer Object for a student's {@link MathProblemAnswer} to a single problem.
 *
 * @param id            the answer ID (null for new answers)
 * @param problemId     the id of the {@link de.tum.cit.aet.artemis.math.domain.MathProblem} this answer belongs to
 * @param scoreInPoints the points earned on this problem (response only, populated after grading)
 * @param steps         the ordered derivation steps for this problem
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MathProblemAnswerDTO(Long id, Long problemId, Double scoreInPoints, List<DerivationStepDTO> steps) {

    /**
     * @param answer the entity to project
     * @return a DTO mirroring the answer
     */
    public static MathProblemAnswerDTO of(MathProblemAnswer answer) {
        Long problemId = answer.getProblem() != null ? answer.getProblem().getId() : null;
        List<DerivationStepDTO> stepDTOs = null;
        List<DerivationStep> steps = answer.getSteps();
        if (steps != null && !steps.isEmpty()) {
            stepDTOs = steps.stream().map(DerivationStepDTO::of).toList();
        }
        return new MathProblemAnswerDTO(answer.getId(), problemId, answer.getScoreInPoints(), stepDTOs);
    }

    /**
     * @return a new {@link MathProblemAnswer} entity populated from this DTO (id is set only if non-null; the problem
     *         association and submission back-reference must be wired by the caller)
     */
    public MathProblemAnswer toEntity() {
        MathProblemAnswer answer = new MathProblemAnswer();
        if (id != null) {
            answer.setId(id);
        }
        answer.setScoreInPoints(scoreInPoints);
        if (steps != null) {
            answer.setSteps(new ArrayList<>(steps.stream().map(DerivationStepDTO::toEntity).toList()));
        }
        return answer;
    }
}
