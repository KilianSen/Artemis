package de.tum.cit.aet.artemis.math.regate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.math.domain.DerivationRole;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.GoalMode;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.domain.StepKind;
import de.tum.cit.aet.artemis.math.domain.blocks.AddBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.EqualityBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.NumberBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.PowBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.VariableBlockDefinition;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/**
 * Pure unit tests for Regate request assembly across the three modes. No Spring context — a minimal
 * {@link BlockRegistry} supplies the inline ruleset. Verifies the mode-specific exercise shape and the
 * induction base/step split by {@link DerivationRole}.
 */
class RegateRequestMapperTest {

    private final BlockRegistry registry = registry();

    private static BlockRegistry registry() {
        BlockRegistry r = new BlockRegistry(
                List.of(new NumberBlockDefinition(), new VariableBlockDefinition(), new AddBlockDefinition(), new EqualityBlockDefinition(), new PowBlockDefinition()));
        r.index();
        return r;
    }

    private static DerivationStep step(int index, String rule, DerivationRole role) {
        DerivationStep s = new DerivationStep();
        s.setStepIndex(index);
        s.setAppliedRuleId(rule);
        s.setTargetNodePath(List.of());
        s.setResultExpression(MathNodes.var("x"));
        s.setDerivationRole(role);
        return s;
    }

    @Test
    void transformationRequestCarriesSourceAndTarget() {
        MathProblem p = new MathProblem();
        p.setGoalMode(GoalMode.TRANSFORMATION);
        p.setSourceExpression(MathNodes.add(MathNodes.var("x"), MathNodes.num("0")));
        p.setTargetExpression(MathNodes.var("x"));

        GradeRequest req = RegateRequestMapper.toRequest(p, List.of(step(0, "add_zero_right", DerivationRole.MAIN)), registry);

        assertThat(req.exercise().mode()).isEqualTo("transformation");
        assertThat(req.exercise().source().getType()).isEqualTo("add");
        assertThat(req.exercise().target().getType()).isEqualTo("variable");
        assertThat(req.exercise().ruleset()).isNotEmpty();
        assertThat(req.submission().steps()).hasSize(1);
        assertThat(req.submission().base()).isNull();
    }

    @Test
    void equationRequestCarriesGoalAsSourceWithNullTarget() {
        MathProblem p = new MathProblem();
        p.setGoalMode(GoalMode.EQUATION);
        p.setGoalExpression(MathNodes.eq(MathNodes.add(MathNodes.var("x"), MathNodes.num("0")), MathNodes.var("x")));

        GradeRequest req = RegateRequestMapper.toRequest(p, List.of(), registry);

        assertThat(req.exercise().mode()).isEqualTo("equation");
        assertThat(req.exercise().source().getType()).isEqualTo("eq");
        assertThat(req.exercise().target()).isNull();
    }

    @Test
    void inductionRequestSplitsBaseAndStepAndCarriesDefinitions() {
        MathProblem p = new MathProblem();
        p.setGoalMode(GoalMode.INDUCTION);
        p.setGoalExpression(MathNodes.eq(MathNodes.pow(MathNodes.num("1"), MathNodes.var("n")), MathNodes.num("1")));
        p.setInductionVariable("n");

        List<DerivationStep> steps = List.of(step(0, "pow_zero", DerivationRole.BASE), step(0, "pow_succ", DerivationRole.STEP), step(1, "mul_one_left", DerivationRole.STEP));

        GradeRequest req = RegateRequestMapper.toRequest(p, steps, registry);

        assertThat(req.exercise().mode()).isEqualTo("induction");
        assertThat(req.exercise().goal().getType()).isEqualTo("eq");
        assertThat(req.exercise().inductionVar()).isEqualTo("n");
        assertThat(req.exercise().source()).isNull();
        // definitions are code-contributed by PowBlockDefinition (pow_zero, pow_succ), not per-problem authored data
        assertThat(req.exercise().definitions()).extracting(d -> d.id()).contains("pow_zero", "pow_succ");
        assertThat(req.submission().steps()).isNull();
        assertThat(req.submission().base().steps()).hasSize(1);
        assertThat(req.submission().step().steps()).hasSize(2);
    }

    @Test
    void kindBStepCarriesTheSubstitutedEquation() {
        MathProblem p = new MathProblem();
        p.setGoalMode(GoalMode.INDUCTION);
        p.setGoalExpression(MathNodes.eq(MathNodes.pow(MathNodes.num("1"), MathNodes.var("n")), MathNodes.num("1")));
        p.setInductionVariable("n");

        DerivationStep ih = new DerivationStep();
        ih.setAppliedRuleId("induction_hypothesis");
        ih.setTargetNodePath(List.of());
        ih.setResultExpression(MathNodes.num("1"));
        ih.setDerivationRole(DerivationRole.STEP);
        ih.setKind(StepKind.B);
        ih.setSubstitutionEquation(MathNodes.eq(MathNodes.pow(MathNodes.num("1"), MathNodes.var("n")), MathNodes.num("1")));

        GradeRequest req = RegateRequestMapper.toRequest(p, List.of(ih), registry);

        var sent = req.submission().step().steps().getFirst();
        assertThat(sent.kind()).isEqualTo("B");
        assertThat(sent.equation()).hasSize(2);
        assertThat(sent.equation().getFirst().getType()).isEqualTo("pow");
        assertThat(sent.equation().get(1).getValue()).isEqualTo("1");
    }
}
