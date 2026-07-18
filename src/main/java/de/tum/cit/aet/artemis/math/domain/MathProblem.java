package de.tum.cit.aet.artemis.math.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO.DerivationStepDTO;
import de.tum.cit.aet.artemis.math.grader.GraderType;

/**
 * A single math problem (question) inside a {@link MathExercise}. A former single-problem exercise is simply the
 * {@code N = 1} case. Each problem carries its own {@link MathProblemConfig grader configuration} so the shared grading
 * engine can grade it independently, and contributes {@link #getPoints() points} toward the exercise's aggregate score.
 */
@Entity
@Table(name = "math_problem")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathProblem extends DomainObject implements MathProblemConfig {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "math_exercise_id")
    @JsonIgnore
    private MathExercise exercise;

    @Column(name = "title")
    private String title;

    @Column(name = "points")
    private double points;

    @Convert(converter = MathNodeConverter.class)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "source_expression")
    private MathNode sourceExpression;

    @Convert(converter = MathNodeConverter.class)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "target_expression")
    private MathNode targetExpression;

    @Convert(converter = MathNodeConverter.class)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "goal_expression")
    private MathNode goalExpression;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_mode", length = 16, nullable = false)
    private GoalMode goalMode = GoalMode.TRANSFORMATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "grader_type", length = 32, nullable = false)
    private GraderType graderType = GraderType.REWRITE_CHAIN;

    /**
     * Optional second, slower formal certifier (Phase 2b). After the primary {@link #graderType} returns a fast
     * preliminary verdict, this backend re-grades to certify (and, being the formal prover, is authoritative if
     * it disagrees). {@code null} means single-backend grading.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "certifying_grader_type", length = 32)
    private GraderType certifyingGraderType;

    @Column(name = "partial_credit_enabled")
    private boolean partialCreditEnabled = false;

    @Column(name = "ac_normalization")
    private boolean acNormalization = false;

    @Column(name = "only_show_applicable_rules")
    private boolean onlyShowApplicableRules = false;

    @Column(name = "allow_verification")
    private boolean allowVerification = true;

    @Column(name = "manual_derivation")
    private boolean manualDerivation = false;

    @Convert(converter = DerivationStepListConverter.class)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "example_derivations")
    private List<DerivationStepDTO> exampleDerivations = Collections.emptyList();

    @Column(name = "induction_variable", length = 64)
    private String inductionVariable;

    @Enumerated(EnumType.STRING)
    @Column(name = "induction_datatype", length = 16, nullable = false)
    private InductionDatatype inductionDatatype = InductionDatatype.NAT;

    public MathExercise getExercise() {
        return exercise;
    }

    public void setExercise(MathExercise exercise) {
        this.exercise = exercise;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getPoints() {
        return points;
    }

    public void setPoints(double points) {
        this.points = points;
    }

    @Override
    public MathNode getSourceExpression() {
        return sourceExpression;
    }

    public void setSourceExpression(MathNode sourceExpression) {
        this.sourceExpression = sourceExpression;
    }

    @Override
    public MathNode getTargetExpression() {
        return targetExpression;
    }

    public void setTargetExpression(MathNode targetExpression) {
        this.targetExpression = targetExpression;
    }

    @Override
    public MathNode getGoalExpression() {
        return goalExpression;
    }

    public void setGoalExpression(MathNode goalExpression) {
        this.goalExpression = goalExpression;
    }

    @Override
    public GoalMode getGoalMode() {
        return goalMode;
    }

    public void setGoalMode(GoalMode goalMode) {
        this.goalMode = goalMode == null ? GoalMode.TRANSFORMATION : goalMode;
    }

    @Override
    public GraderType getGraderType() {
        return graderType;
    }

    public void setGraderType(GraderType graderType) {
        this.graderType = graderType == null ? GraderType.REWRITE_CHAIN : graderType;
    }

    @Override
    public GraderType getCertifyingGraderType() {
        return certifyingGraderType;
    }

    public void setCertifyingGraderType(GraderType certifyingGraderType) {
        this.certifyingGraderType = certifyingGraderType;
    }

    @Override
    public boolean isPartialCreditEnabled() {
        return partialCreditEnabled;
    }

    public void setPartialCreditEnabled(boolean partialCreditEnabled) {
        this.partialCreditEnabled = partialCreditEnabled;
    }

    @Override
    public boolean isAcNormalization() {
        return acNormalization;
    }

    public void setAcNormalization(boolean acNormalization) {
        this.acNormalization = acNormalization;
    }

    public boolean isOnlyShowApplicableRules() {
        return onlyShowApplicableRules;
    }

    public void setOnlyShowApplicableRules(boolean onlyShowApplicableRules) {
        this.onlyShowApplicableRules = onlyShowApplicableRules;
    }

    public boolean isAllowVerification() {
        return allowVerification;
    }

    public void setAllowVerification(boolean allowVerification) {
        this.allowVerification = allowVerification;
    }

    public boolean isManualDerivation() {
        return manualDerivation;
    }

    public void setManualDerivation(boolean manualDerivation) {
        this.manualDerivation = manualDerivation;
    }

    public List<DerivationStepDTO> getExampleDerivations() {
        return exampleDerivations;
    }

    public void setExampleDerivations(List<DerivationStepDTO> exampleDerivations) {
        this.exampleDerivations = exampleDerivations != null ? new ArrayList<>(exampleDerivations) : Collections.emptyList();
    }

    @Override
    public String getInductionVariable() {
        return inductionVariable;
    }

    public void setInductionVariable(String inductionVariable) {
        this.inductionVariable = inductionVariable;
    }

    @Override
    public InductionDatatype getInductionDatatype() {
        return inductionDatatype;
    }

    public void setInductionDatatype(InductionDatatype inductionDatatype) {
        this.inductionDatatype = inductionDatatype == null ? InductionDatatype.NAT : inductionDatatype;
    }

    @Override
    public String toString() {
        return "MathProblem{" + "id=" + getId() + ", title='" + title + "'}";
    }
}
