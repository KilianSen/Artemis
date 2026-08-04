package de.tum.cit.aet.artemis.math.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.GoalMode;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.domain.MathProblemConfig;
import de.tum.cit.aet.artemis.math.grader.GraderRegistry;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import de.tum.cit.aet.artemis.math.grader.GradingResult;
import de.tum.cit.aet.artemis.math.grader.MathGrader;

/**
 * Unit tests for {@link MathGradingService}'s multi-grader routing: a problem may configure several grader backends,
 * and grading runs the ones that support the goal mode in order, taking the first conclusive verdict.
 */
class MathGradingServiceMultiGraderTest {

    /** A stub grader that records how often it was invoked and returns a canned verdict. */
    private static final class RecordingGrader implements MathGrader {

        private final GraderType type;

        private final GradingResult result;

        private int calls = 0;

        private RecordingGrader(GraderType type, GradingResult result) {
            this.type = type;
            this.result = result;
        }

        @Override
        public GraderType getType() {
            return type;
        }

        @Override
        public GradingResult grade(MathProblemConfig config, List<DerivationStep> steps) {
            calls++;
            return result;
        }
    }

    private static MathGradingService serviceWith(MathGrader... graders) {
        GraderRegistry registry = new GraderRegistry(List.of(graders));
        registry.index();
        return new MathGradingService(registry);
    }

    private static MathProblem problem(GoalMode mode, List<GraderType> graderTypes) {
        MathProblem problem = new MathProblem();
        problem.setGoalMode(mode);
        problem.setGraderTypes(graderTypes);
        return problem;
    }

    /** A single non-empty step so the empty-remote-submission short-circuit does not fire. */
    private static List<DerivationStep> steps() {
        return List.of(new DerivationStep());
    }

    @Test
    void firstConclusiveGraderWins_andShortCircuitsTheRest() {
        RecordingGrader primary = new RecordingGrader(GraderType.EGGREGATE, GradingResult.of(100.0));
        RecordingGrader secondary = new RecordingGrader(GraderType.PATH_CHECKER, GradingResult.of(0.0));
        MathGradingService service = serviceWith(primary, secondary);

        GradingResult result = service.gradeProblem(problem(GoalMode.TRANSFORMATION, List.of(GraderType.EGGREGATE, GraderType.PATH_CHECKER)), steps());

        assertThat(result.conclusive()).isTrue();
        assertThat(result.score()).isEqualTo(100.0);
        assertThat(primary.calls).isEqualTo(1);
        assertThat(secondary.calls).isZero();
    }

    @Test
    void weakerGraderFullPassAfterStrongerAbstention_isAccepted() {
        // eggregate (stronger) is down; the path checker (weaker) fully verifies the derivation → its pass is sound.
        RecordingGrader down = new RecordingGrader(GraderType.EGGREGATE, GradingResult.inconclusive("backend down"));
        RecordingGrader fallback = new RecordingGrader(GraderType.PATH_CHECKER, GradingResult.of(100.0));
        MathGradingService service = serviceWith(down, fallback);

        GradingResult result = service.gradeProblem(problem(GoalMode.TRANSFORMATION, List.of(GraderType.EGGREGATE, GraderType.PATH_CHECKER)), steps());

        assertThat(result.conclusive()).isTrue();
        assertThat(result.score()).isEqualTo(100.0);
        assertThat(down.calls).isEqualTo(1);
        assertThat(fallback.calls).isEqualTo(1);
    }

    @Test
    void weakerGraderFailAfterStrongerAbstention_routesToReview() {
        // eggregate (stronger) is down; the path checker (weaker) rejects the answer — but it may just lack the rules to
        // verify it, so its fail must not override the stronger grader's abstention. Route to review instead.
        RecordingGrader down = new RecordingGrader(GraderType.EGGREGATE, GradingResult.inconclusive("backend down"));
        RecordingGrader weakFail = new RecordingGrader(GraderType.PATH_CHECKER, GradingResult.of(0.0));
        MathGradingService service = serviceWith(down, weakFail);

        GradingResult result = service.gradeProblem(problem(GoalMode.TRANSFORMATION, List.of(GraderType.EGGREGATE, GraderType.PATH_CHECKER)), steps());

        assertThat(result.conclusive()).isFalse();
        assertThat(down.calls).isEqualTo(1);
        assertThat(weakFail.calls).isEqualTo(1);
    }

    @Test
    void strongerGraderFailAfterWeakerAbstention_isAuthoritative() {
        // The path checker (weaker) abstains; eggregate (stronger) then rejects the answer → its fail is authoritative.
        RecordingGrader weakAbstain = new RecordingGrader(GraderType.PATH_CHECKER, GradingResult.inconclusive("cannot decide"));
        RecordingGrader strongFail = new RecordingGrader(GraderType.EGGREGATE, GradingResult.of(0.0));
        MathGradingService service = serviceWith(weakAbstain, strongFail);

        GradingResult result = service.gradeProblem(problem(GoalMode.TRANSFORMATION, List.of(GraderType.PATH_CHECKER, GraderType.EGGREGATE)), steps());

        assertThat(result.conclusive()).isTrue();
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(weakAbstain.calls).isEqualTo(1);
        assertThat(strongFail.calls).isEqualTo(1);
    }

    @Test
    void standalonePathCheckerFail_isConclusive() {
        // No stronger grader abstained, so the path checker's own fail is authoritative (no regression for pure path-check exercises).
        RecordingGrader pathChecker = new RecordingGrader(GraderType.PATH_CHECKER, GradingResult.of(0.0));
        MathGradingService service = serviceWith(pathChecker);

        GradingResult result = service.gradeProblem(problem(GoalMode.TRANSFORMATION, List.of(GraderType.PATH_CHECKER)), steps());

        assertThat(result.conclusive()).isTrue();
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(pathChecker.calls).isEqualTo(1);
    }

    @Test
    void onlyGradersSupportingTheGoalModeAreRun() {
        // EGGREGATE supports TRANSFORMATION; COQREGATE is induction-only and must be skipped for a transformation problem.
        RecordingGrader transformation = new RecordingGrader(GraderType.EGGREGATE, GradingResult.of(100.0));
        RecordingGrader induction = new RecordingGrader(GraderType.COQREGATE, GradingResult.of(0.0));
        MathGradingService service = serviceWith(transformation, induction);

        GradingResult result = service.gradeProblem(problem(GoalMode.TRANSFORMATION, List.of(GraderType.EGGREGATE, GraderType.COQREGATE)), steps());

        assertThat(result.score()).isEqualTo(100.0);
        assertThat(transformation.calls).isEqualTo(1);
        assertThat(induction.calls).isZero();
    }

    @Test
    void allGradersInconclusive_routesToReview() {
        RecordingGrader a = new RecordingGrader(GraderType.EGGREGATE, GradingResult.inconclusive("a down"));
        RecordingGrader b = new RecordingGrader(GraderType.LEANREGATE, GradingResult.inconclusive("b down"));
        MathGradingService service = serviceWith(a, b);

        GradingResult result = service.gradeProblem(problem(GoalMode.TRANSFORMATION, List.of(GraderType.EGGREGATE, GraderType.LEANREGATE)), steps());

        assertThat(result.conclusive()).isFalse();
        assertThat(a.calls).isEqualTo(1);
        assertThat(b.calls).isEqualTo(1);
    }

    @Test
    void emptyGraderList_fallsBackToPathChecker() {
        RecordingGrader rewrite = new RecordingGrader(GraderType.PATH_CHECKER, GradingResult.of(42.0));
        MathGradingService service = serviceWith(rewrite);

        GradingResult result = service.gradeProblem(problem(GoalMode.TRANSFORMATION, List.of()), steps());

        assertThat(result.score()).isEqualTo(42.0);
        assertThat(rewrite.calls).isEqualTo(1);
    }
}
