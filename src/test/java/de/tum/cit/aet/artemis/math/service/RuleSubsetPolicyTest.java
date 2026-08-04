package de.tum.cit.aet.artemis.math.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.math.domain.BlockDefinition;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.domain.StepKind;
import de.tum.cit.aet.artemis.math.domain.blocks.AddBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.ApplyBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.MulBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.NumberBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.PowBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.VariableBlockDefinition;

/**
 * Pure unit tests for {@link RuleSubsetPolicy} — the single interpretation of a problem's allowed rule subset,
 * including its two load-bearing exemptions (trusted recursive definitions and kind-B Leibniz steps).
 */
class RuleSubsetPolicyTest {

    private RuleSubsetPolicy policy;

    @BeforeEach
    void setUp() {
        List<BlockDefinition> blocks = List.of(new NumberBlockDefinition(), new VariableBlockDefinition(), new AddBlockDefinition(), new MulBlockDefinition(),
                new PowBlockDefinition(), new ApplyBlockDefinition());
        BlockRegistry registry = new BlockRegistry(blocks);
        registry.index();
        policy = new RuleSubsetPolicy(registry);
    }

    private static MathProblem restricted(List<String> allowedRuleIds) {
        MathProblem problem = new MathProblem();
        problem.setAllowedRuleIds(allowedRuleIds);
        return problem;
    }

    private static DerivationStep step(String ruleId, StepKind kind) {
        DerivationStep step = new DerivationStep();
        step.setAppliedRuleId(ruleId);
        step.setKind(kind);
        return step;
    }

    @Test
    void aProblemWithoutASubset_isUnrestricted() {
        assertThat(RuleSubsetPolicy.isUnrestricted(new MathProblem())).isTrue();
        assertThat(policy.isRuleAllowed(new MathProblem(), "mul_comm")).isTrue();
    }

    @Test
    void anEmptySubset_isUnrestricted() {
        assertThat(RuleSubsetPolicy.isUnrestricted(restricted(List.of()))).isTrue();
        assertThat(policy.isRuleAllowed(restricted(List.of()), "mul_comm")).isTrue();
    }

    @Test
    void aSubsetAdmitsOnlyItsOwnRules() {
        MathProblem problem = restricted(List.of("add_zero_left"));
        assertThat(policy.isRuleAllowed(problem, "add_zero_left")).isTrue();
        assertThat(policy.isRuleAllowed(problem, "mul_comm")).isFalse();
        assertThat(policy.isRuleAllowed(problem, null)).isFalse();
        assertThat(policy.isRuleAllowed(problem, "rule_that_does_not_exist")).isFalse();
    }

    /**
     * Recursive definitions are definitional and therefore always trusted (GRADING_PROTOCOL.md); the backends merge
     * them into the citable rule table regardless of the ruleset. Restricting them would break every induction
     * submission, so the subset must never reach them.
     */
    @Test
    void recursiveDefinitionsAreAlwaysAllowed() {
        MathProblem problem = restricted(List.of("add_zero_left"));
        assertThat(policy.isRuleAllowed(problem, "pow_succ")).isTrue();
        assertThat(policy.isRuleAllowed(problem, "pow_zero")).isTrue();
        assertThat(policy.isRuleAllowed(problem, "fact_succ")).isTrue();
    }

    /**
     * Kind-B steps carry ids the client mints for the induction hypothesis; they exist in no registry, so checking
     * them would reject every inductive step. Their soundness comes from the substituted equation, not the id.
     */
    @Test
    void kindBStepsAreNeverChecked() {
        MathProblem problem = restricted(List.of("add_zero_left"));
        assertThat(policy.isStepAllowed(problem, step("induction_hypothesis", StepKind.B))).isTrue();
        assertThat(policy.isStepAllowed(problem, step("induction_hypothesis_l", StepKind.B))).isTrue();
        // The same fabricated id on a kind-A step is not exempt.
        assertThat(policy.isStepAllowed(problem, step("induction_hypothesis", StepKind.A))).isFalse();
    }

    @Test
    void firstDisallowedStepIndex_findsTheFirstOffender() {
        MathProblem problem = restricted(List.of("add_zero_left", "pow_succ"));
        List<DerivationStep> steps = List.of(step("add_zero_left", StepKind.A), step("pow_zero", StepKind.A), step("mul_comm", StepKind.A), step("mul_one_left", StepKind.A));

        assertThat(policy.firstDisallowedStepIndex(problem, steps)).hasValue(2);
        assertThat(policy.firstDisallowedStepIndex(new MathProblem(), steps)).isEmpty();
        assertThat(policy.firstDisallowedStepIndex(problem, List.of())).isEmpty();
    }

    @Test
    void knownRuleIds_coverCatalogueRulesAndDefinitions() {
        assertThat(policy.knownRuleIds()).contains("add_zero_left", "mul_comm", "pow_succ", "fact_zero").doesNotContain("induction_hypothesis", "not_a_rule");
    }
}
