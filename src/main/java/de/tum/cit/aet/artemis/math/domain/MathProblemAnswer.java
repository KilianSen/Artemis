package de.tum.cit.aet.artemis.math.domain;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

    /** The backend verdict category (e.g. {@code PROVEN_EQUAL}) for this answer; {@code null} for the in-process grader. */
    @Column(name = "grading_outcome", length = 32)
    private String gradingOutcome;

    /** Whether the verdict is backed by a re-checked proof (formal backend). */
    @Column(name = "certified")
    private boolean certified = false;

    /** True while the fast preliminary verdict is set but a configured slow certifier has not yet returned (Phase 2b). */
    @Column(name = "certification_pending")
    private boolean certificationPending = false;

    /** Human-readable grader feedback for this answer. */
    @Column(name = "grading_feedback", length = 1024)
    private String gradingFeedback;

    /** A counterexample assignment (e.g. {@code "x=0"}) for a disproof verdict; else {@code null}. */
    @Column(name = "witness", length = 512)
    private String witness;

    // Ordered by @OrderBy (not @OrderColumn): a mapped-by @OneToMany with @OrderColumn is a fragile Hibernate
    // combination — clearing the collection on update nulls the order column and a reload throws "Illegal null
    // value for list index". Sorting by (role, stepIndex) needs no managed column. Within a role stepIndex is
    // unique and monotonic; MAIN never coexists with BASE/STEP, so the order is deterministic.
    // A Set (not a List): a mapped-by @OneToMany List with @OrderColumn is a fragile Hibernate combination (clearing
    // the collection on update nulls the order column and a reload throws "Illegal null value for list index"), while
    // a List without @OrderColumn is a bag that cannot be fetch-joined alongside MathSubmission.answers
    // (MultipleBagFetchException). A Set ordered by @OrderBy avoids both. DerivationStep uses identity equality, so
    // unsaved steps are not collapsed; ordering by (role, stepIndex) is deterministic (MAIN never mixes with BASE/STEP).
    @OneToMany(mappedBy = "answer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("derivationRole, stepIndex")
    private Set<DerivationStep> steps = new LinkedHashSet<>();

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

    public String getGradingOutcome() {
        return gradingOutcome;
    }

    public void setGradingOutcome(String gradingOutcome) {
        this.gradingOutcome = gradingOutcome;
    }

    public boolean isCertified() {
        return certified;
    }

    public void setCertified(boolean certified) {
        this.certified = certified;
    }

    public boolean isCertificationPending() {
        return certificationPending;
    }

    public void setCertificationPending(boolean certificationPending) {
        this.certificationPending = certificationPending;
    }

    public String getGradingFeedback() {
        return gradingFeedback;
    }

    public void setGradingFeedback(String gradingFeedback) {
        this.gradingFeedback = gradingFeedback;
    }

    public String getWitness() {
        return witness;
    }

    public void setWitness(String witness) {
        this.witness = witness;
    }

    public Set<DerivationStep> getSteps() {
        return steps;
    }

    public void setSteps(Set<DerivationStep> steps) {
        this.steps = steps != null ? steps : new LinkedHashSet<>();
    }

    @Override
    public String toString() {
        return "MathProblemAnswer{" + "id=" + getId() + "}";
    }
}
