package de.tum.cit.aet.artemis.math.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A student's answer to a single {@link MathProblem} within a {@link MathSubmission}. Holds the ordered
 * {@link DerivationStep derivation steps} the student produced for that problem and the per-problem score in points
 * assigned by the aggregate grader.
 */
@Entity
@Table(name = "math_problem_answer")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathProblemAnswer extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    @JsonIgnore
    private MathSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private MathProblem problem;

    @Column(name = "score_in_points")
    private Double scoreInPoints;

    @OneToMany(mappedBy = "answer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderColumn(name = "steps_order")
    private List<DerivationStep> steps = new ArrayList<>();

    public MathSubmission getSubmission() {
        return submission;
    }

    public void setSubmission(MathSubmission submission) {
        this.submission = submission;
    }

    public MathProblem getProblem() {
        return problem;
    }

    public void setProblem(MathProblem problem) {
        this.problem = problem;
    }

    public Double getScoreInPoints() {
        return scoreInPoints;
    }

    public void setScoreInPoints(Double scoreInPoints) {
        this.scoreInPoints = scoreInPoints;
    }

    public List<DerivationStep> getSteps() {
        return steps;
    }

    public void setSteps(List<DerivationStep> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "MathProblemAnswer{" + "id=" + getId() + "}";
    }
}
