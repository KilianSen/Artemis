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
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.domain.blocks.AddBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.EqualityBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.NumberBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.VariableBlockDefinition;
import de.tum.cit.aet.artemis.math.grader.GradingResult;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest;
import de.tum.cit.aet.artemis.math.regate.dto.GradeResponse;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/**
 * Live smoke test against a running Regate backend — exercises the real wire boundary end to end:
 * Artemis assembles a {@link GradeRequest} with its own mappers, POSTs it to the backend over HTTP, and
 * maps the {@link GradeResponse} back to a {@link GradingResult}. This is the one thing the unit/integration
 * suites cannot cover (they never touch a real backend). Inert unless the {@code REGATE_LIVE_URL} env var is set,
 * so CI and the normal suites skip it.
 */
@EnabledIfEnvironmentVariable(named = "REGATE_LIVE_URL", matches = ".+")
class LiveEggregateGradingTest {

    private static final Logger log = LoggerFactory.getLogger(LiveEggregateGradingTest.class);

    private static final String URL = System.getenv("REGATE_LIVE_URL");

    private final RegateClient client = new RegateClient();

    private static BlockRegistry registry() {
        BlockRegistry r = new BlockRegistry(List.of(new NumberBlockDefinition(), new VariableBlockDefinition(), new AddBlockDefinition(), new EqualityBlockDefinition()));
        r.index();
        return r;
    }

    private static DerivationStep step(int index, String rule, List<Integer> path) {
        DerivationStep s = new DerivationStep();
        s.setStepIndex(index);
        s.setAppliedRuleId(rule);
        s.setTargetNodePath(path);
        s.setResultExpression(MathNodes.var("x"));
        s.setDerivationRole(DerivationRole.MAIN);
        return s;
    }

    @Test
    void transformationGradedByLiveBackend() {
        // Transform x + 0 into x by applying the code-contributed rule add_zero_right (a + 0 -> a).
        MathProblem p = new MathProblem();
        p.setGoalMode(GoalMode.TRANSFORMATION);
        p.setSourceExpression(MathNodes.add(MathNodes.var("x"), MathNodes.num("0")));
        p.setTargetExpression(MathNodes.var("x"));

        GradeRequest req = RegateRequestMapper.toRequest(p, List.of(step(0, "add_zero_right", List.of())), registry());
        GradeResponse resp = client.grade(URL, req);
        GradingResult result = RegateResponseMapper.toGradingResult(resp);

        log.info("[live transformation] backend={} outcome={} score={} certified={} feedback={}", resp.backend(), result.outcome(), result.score(), result.certified(),
                result.message());

        assertThat(resp.outcome()).isNotNull();
        assertThat(result.conclusive()).isTrue();
        assertThat(result.outcome()).isEqualTo("PROVEN_EQUAL");
        assertThat(result.score()).isBetween(0.0, 100.0);
    }

    @Test
    void equationGradedByLiveBackend() {
        // Equation mode: the goal x + 0 = x reduces to a tautology; the backend proves the two sides equal.
        MathProblem p = new MathProblem();
        p.setGoalMode(GoalMode.EQUATION);
        p.setGoalExpression(MathNodes.eq(MathNodes.add(MathNodes.var("x"), MathNodes.num("0")), MathNodes.var("x")));

        // One real step: apply add_zero_right to the left side (path [0]) to reduce x + 0 = x into the tautology x = x.
        DerivationStep reduce = new DerivationStep();
        reduce.setStepIndex(0);
        reduce.setAppliedRuleId("add_zero_right");
        reduce.setTargetNodePath(List.of(0));
        reduce.setResultExpression(MathNodes.eq(MathNodes.var("x"), MathNodes.var("x")));
        reduce.setDerivationRole(DerivationRole.MAIN);

        GradeRequest req = RegateRequestMapper.toRequest(p, List.of(reduce), registry());
        GradeResponse resp = client.grade(URL, req);
        GradingResult result = RegateResponseMapper.toGradingResult(resp);

        log.info("[live equation] backend={} outcome={} score={} conclusive={}", resp.backend(), result.outcome(), result.score(), result.conclusive());

        // The round-trip must succeed and yield a categorised verdict; the backend decides equal here.
        assertThat(resp.outcome()).isNotNull();
        assertThat(result.outcome()).isEqualTo("PROVEN_EQUAL");
    }
}
