package de.tum.cit.aet.artemis.math.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
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

    /**
     * The grader backends that grade this problem, in preference order. At grade time the graders that support the
     * problem's {@link #goalMode} are run in this order and the first conclusive verdict wins; an inconclusive result
     * (e.g. a remote backend outage) falls through to the next. Selecting more than one backend therefore adds
     * redundancy (same-mode graders) or mode coverage (disjoint-mode graders). Never empty — defaults to
     * {@link GraderType#PATH_CHECKER}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "math_problem_grader_types", joinColumns = @JoinColumn(name = "math_problem_id"))
    @OrderColumn(name = "position")
    @Enumerated(EnumType.STRING)
    @Column(name = "grader_type", length = 32, nullable = false)
    private List<GraderType> graderTypes = new ArrayList<>(List.of(GraderType.PATH_CHECKER));

    /**
     * Optional second, slower formal certifier (Phase 2b). After the primary {@link #graderTypes} return a fast
     * preliminary verdict, this backend re-grades to certify (and, being the formal prover, is authoritative if
     * it disagrees). {@code null} means no certification pass.
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

    /**
     * The rewrite rules this problem restricts the student to, by {@link RewriteRule#id() rule id}. {@code null} or
     * empty means <em>unrestricted</em> — every catalogue rule is citable, which is what every pre-existing problem
     * gets and therefore leaves current behaviour untouched.
     * <p>
     * Enforcement is server-side and authoritative (see {@code MathGradingService#gradeProblem} and
     * {@code PathCheckerGrader}); the client-side palette filter is cosmetic, since the wire carries a bare rule id
     * that {@code curl} can set freely. Recursive {@code definitions} are never restricted by this list — the
     * protocol treats them as definitional and always trusted, and every induction submission cites them.
     * <p>
     * Stored as a JSON array in one column rather than as an element collection: the entity is join-fetched by
     * several repository queries and a second EAGER collection would multiply those result sets.
     */
    @Convert(converter = StringListConverter.class)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "allowed_rule_ids")
    private List<String> allowedRuleIds = Collections.emptyList();

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
    public List<GraderType> getGraderTypes() {
        return graderTypes;
    }

    public void setGraderTypes(List<GraderType> graderTypes) {
        this.graderTypes = graderTypes == null || graderTypes.isEmpty() ? new ArrayList<>(List.of(GraderType.PATH_CHECKER)) : new ArrayList<>(graderTypes);
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
    public List<String> getAllowedRuleIds() {
        return allowedRuleIds;
    }

    /**
     * Sets the allowed rule subset. Blank entries are dropped and duplicates collapsed (order preserved), so a
     * sloppily authored JSON list still yields a clean subset; {@code null} and empty both mean "unrestricted".
     *
     * @param allowedRuleIds the rule ids the student may cite, or {@code null}/empty for unrestricted
     */
    public void setAllowedRuleIds(List<String> allowedRuleIds) {
        if (allowedRuleIds == null || allowedRuleIds.isEmpty()) {
            this.allowedRuleIds = Collections.emptyList();
            return;
        }
        this.allowedRuleIds = allowedRuleIds.stream().filter(id -> id != null && !id.isBlank()).map(String::trim).distinct().toList();
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
