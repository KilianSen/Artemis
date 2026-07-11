package de.tum.cit.aet.artemis.math.domain;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One step in a student's math derivation.
 * Records which rule was applied, at which node (by index-path), and the full resulting expression tree.
 * Storing the full tree per step makes verification independent of the rule engine version.
 */
@Entity
@Table(name = "math_derivation_step")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DerivationStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    @JsonIgnore
    private MathProblemAnswer answer;

    @Column(name = "step_index")
    private int stepIndex;

    @Column(name = "applied_rule_id")
    private String appliedRuleId;

    @Convert(converter = IntegerListConverter.class)
    @Column(name = "target_node_path", columnDefinition = "longtext")
    private List<Integer> targetNodePath;

    @Convert(converter = MathNodeConverter.class)
    @Column(name = "result_expression", columnDefinition = "longtext")
    private MathNode resultExpression;

    /**
     * Direction in which the rule was applied. {@link StepDirection#FORWARD} (default) replays
     * the rule as {@code pattern → template}; {@link StepDirection#REVERSE} replays it as
     * {@code template → pattern} and is only valid when {@link RewriteRule#direction()} is
     * {@link RuleDirection#BIDIRECTIONAL}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 8, nullable = false)
    private StepDirection direction = StepDirection.FORWARD;

    /**
     * Which derivation of the answer this step belongs to: {@link DerivationRole#MAIN} for a
     * transformation/equation answer, or {@link DerivationRole#BASE}/{@link DerivationRole#STEP} for the two
     * obligations of an induction proof.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "derivation_role", length = 8, nullable = false)
    private DerivationRole derivationRole = DerivationRole.MAIN;

    /**
     * How this step is justified: {@link StepKind#A} (rule application) or {@link StepKind#B} (Leibniz
     * substitution using {@link #substitutionEquation}, e.g. the induction hypothesis).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "step_kind", length = 4, nullable = false)
    private StepKind kind = StepKind.A;

    /** The equality substituted in a {@link StepKind#B} step (e.g. the induction hypothesis {@code P(n)}); else {@code null}. */
    @Convert(converter = MathNodeConverter.class)
    @Column(name = "substitution_equation", columnDefinition = "longtext")
    private MathNode substitutionEquation;

    public void setDirection(StepDirection direction) {
        this.direction = direction == null ? StepDirection.FORWARD : direction;
    }

    public DerivationRole getDerivationRole() {
        return derivationRole;
    }

    public void setDerivationRole(DerivationRole derivationRole) {
        this.derivationRole = derivationRole == null ? DerivationRole.MAIN : derivationRole;
    }

    public StepKind getKind() {
        return kind;
    }

    public void setKind(StepKind kind) {
        this.kind = kind == null ? StepKind.A : kind;
    }

    public MathNode getSubstitutionEquation() {
        return substitutionEquation;
    }

    public void setSubstitutionEquation(MathNode substitutionEquation) {
        this.substitutionEquation = substitutionEquation;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MathProblemAnswer getAnswer() {
        return answer;
    }

    public void setAnswer(MathProblemAnswer answer) {
        this.answer = answer;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    public String getAppliedRuleId() {
        return appliedRuleId;
    }

    public void setAppliedRuleId(String appliedRuleId) {
        this.appliedRuleId = appliedRuleId;
    }

    public List<Integer> getTargetNodePath() {
        return targetNodePath;
    }

    public void setTargetNodePath(List<Integer> targetNodePath) {
        this.targetNodePath = targetNodePath;
    }

    public MathNode getResultExpression() {
        return resultExpression;
    }

    public void setResultExpression(MathNode resultExpression) {
        this.resultExpression = resultExpression;
    }

    public StepDirection getDirection() {
        return direction;
    }
}
