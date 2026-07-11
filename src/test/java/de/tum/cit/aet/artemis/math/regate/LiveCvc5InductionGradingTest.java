package de.tum.cit.aet.artemis.math.regate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.math.domain.DerivationRole;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.GoalMode;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.domain.StepKind;
import de.tum.cit.aet.artemis.math.domain.blocks.EqualityBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.MulBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.NumberBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.PowBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.VariableBlockDefinition;
import de.tum.cit.aet.artemis.math.grader.GradingResult;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest;
import de.tum.cit.aet.artemis.math.regate.dto.GradeResponse;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/**
 * Live smoke test for the <b>induction</b> path against a running cvc5regate backend. Artemis assembles a full
 * induction {@link GradeRequest} (goal + inductionVar + code-contributed definitions, base/step derivations
 * split by {@link DerivationRole}, the induction hypothesis as a kind-B Leibniz step) with its own mappers,
 * POSTs it, and maps the {@link GradeResponse} back. Proves the induction wire contract end to end — the base/
 * step split, the definitions, and the kind-B equation all have to line up with the backend. Inert unless
 * {@code REGATE_CVC5_URL} is set, so CI and the normal suites skip it.
 */
@EnabledIfEnvironmentVariable(named = "REGATE_CVC5_URL", matches = ".+")
class LiveCvc5InductionGradingTest {

    private static final Logger log = LoggerFactory.getLogger(LiveCvc5InductionGradingTest.class);

    private static final String URL = System.getenv("REGATE_CVC5_URL");

    private final RegateClient client = new RegateClient();

    private static BlockRegistry registry() {
        BlockRegistry r = new BlockRegistry(
                List.of(new NumberBlockDefinition(), new VariableBlockDefinition(), new MulBlockDefinition(), new PowBlockDefinition(), new EqualityBlockDefinition()));
        r.index();
        return r;
    }

    private static DerivationStep step(int index, String rule, List<Integer> path, DerivationRole role, MathNode result) {
        DerivationStep s = new DerivationStep();
        s.setStepIndex(index);
        s.setAppliedRuleId(rule);
        s.setTargetNodePath(path);
        s.setResultExpression(result);
        s.setDerivationRole(role);
        return s;
    }

    @Test
    void inductionProofCertifiedByLiveCvc5() {
        // Prove by induction on n: 1^n = 1. Definitions pow_zero / pow_succ are code-contributed by PowBlockDefinition.
        MathProblem p = new MathProblem();
        p.setGoalMode(GoalMode.INDUCTION);
        p.setGoalExpression(MathNodes.eq(MathNodes.pow(MathNodes.num("1"), MathNodes.var("n")), MathNodes.num("1")));
        p.setInductionVariable("n");

        // Base case P(0): 1^0 = 1 --pow_zero at [0]--> 1 = 1
        DerivationStep base = step(0, "pow_zero", List.of(0), DerivationRole.BASE, MathNodes.eq(MathNodes.num("1"), MathNodes.num("1")));

        // Inductive step P(S n): 1^(S n) = 1
        // 1) pow_succ at [0]: 1 * 1^n = 1
        DerivationStep s1 = step(0, "pow_succ", List.of(0), DerivationRole.STEP,
                MathNodes.eq(MathNodes.mul(MathNodes.num("1"), MathNodes.pow(MathNodes.num("1"), MathNodes.var("n"))), MathNodes.num("1")));
        // 2) Induction hypothesis (kind B) at [0, 1] with equation 1^n = 1: 1 * 1 = 1
        DerivationStep ih = new DerivationStep();
        ih.setStepIndex(1);
        ih.setTargetNodePath(List.of(0, 1));
        ih.setDerivationRole(DerivationRole.STEP);
        ih.setKind(StepKind.B);
        ih.setSubstitutionEquation(MathNodes.eq(MathNodes.pow(MathNodes.num("1"), MathNodes.var("n")), MathNodes.num("1")));
        ih.setResultExpression(MathNodes.eq(MathNodes.mul(MathNodes.num("1"), MathNodes.num("1")), MathNodes.num("1")));
        // 3) mul_one_left at [0]: 1 = 1
        DerivationStep s3 = step(2, "mul_one_left", List.of(0), DerivationRole.STEP, MathNodes.eq(MathNodes.num("1"), MathNodes.num("1")));

        GradeRequest req = RegateRequestMapper.toRequest(p, List.of(base, s1, ih, s3), registry());
        GradeResponse resp = client.grade(URL, req);
        GradingResult result = RegateResponseMapper.toGradingResult(resp);

        log.info("[live induction] backend={} outcome={} score={} certified={} feedback={}", resp.backend(), result.outcome(), result.score(), result.certified(),
                result.message());

        assertThat(resp.outcome()).isNotNull();
        assertThat(result.outcome()).isEqualTo("PROVEN_EQUAL");
        assertThat(result.conclusive()).isTrue();
        assertThat(result.certified()).isTrue();
        assertThat(result.score()).isEqualTo(100.0);
    }
}
