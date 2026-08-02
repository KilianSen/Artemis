package de.tum.cit.aet.artemis.math.domain;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

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

    // A Set (not a List), for the same reason MathProblemAnswer.steps is one: a List without @OrderColumn is a bag,
    // and a bag fetch-joined alongside the nested a.steps collection yields one duplicate answer per step row —
    // 17 steps produced 17 copies of the same answer, inflating anything that folds over the collection (the
    // assessment view's earned-points sum showed "17 / 1"). A Set de-duplicates by identity. MathProblemAnswer
    // inherits DomainObject.equals, which is false whenever either id is null, so unsaved answers are never
    // collapsed; @OrderBy keeps the iteration order deterministic without a managed order column.
    @JsonIgnore
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    private Set<MathProblemAnswer> answers = new LinkedHashSet<>();

    public Set<MathProblemAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(Set<MathProblemAnswer> answers) {
        this.answers = answers != null ? answers : new LinkedHashSet<>();
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
