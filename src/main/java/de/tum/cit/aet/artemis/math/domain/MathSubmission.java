package de.tum.cit.aet.artemis.math.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * A MathSubmission.
 * <p>
 * Holds one {@link MathProblemAnswer} per {@link MathProblem} the student worked on, mirroring the way a quiz
 * submission holds one submitted answer per quiz question.
 */
@Entity
@DiscriminatorValue(value = "R")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathSubmission extends Submission {

    @JsonIgnore
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MathProblemAnswer> answers = new ArrayList<>();

    public List<MathProblemAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<MathProblemAnswer> answers) {
        this.answers = answers != null ? answers : new ArrayList<>();
    }

    /**
     * Finds the answer this submission carries for the given problem.
     *
     * @param problemId the id of the {@link MathProblem} whose answer to look up
     * @return the matching {@link MathProblemAnswer}, or {@code null} if this submission has no answer for that problem
     */
    @JsonIgnore
    public MathProblemAnswer answerForProblem(Long problemId) {
        if (problemId == null) {
            return null;
        }
        return answers.stream().filter(answer -> answer.getProblem() != null && Objects.equals(answer.getProblem().getId(), problemId)).findFirst().orElse(null);
    }

    @Override
    public String getSubmissionExerciseType() {
        return "math";
    }

    @JsonIgnore
    @Override
    public boolean isEmpty() {
        return answers == null || answers.isEmpty();
    }

    @Override
    public String toString() {
        return "MathSubmission{" + "id=" + getId() + "}";
    }
}
