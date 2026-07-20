package de.tum.cit.aet.artemis.math.regate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest.Derivation;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest.ExerciseSpec;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest.SubmissionSpec;
import de.tum.cit.aet.artemis.math.regate.dto.RuleSpec;
import de.tum.cit.aet.artemis.math.regate.dto.StepSpec;

/**
 * {@link RegateVocabulary} flags a request as using protocol-1.1 extended vocabulary iff an {@code apply} node
 * appears anywhere the backend must parse it — the signal {@link AbstractRegateGrader} uses to turn a backend's
 * {@code 400} into an instructor-facing configuration error.
 */
class RegateVocabularyTest {

    private static ExerciseSpec exercise(de.tum.cit.aet.artemis.math.domain.MathNode goal, List<RuleSpec> definitions) {
        return new ExerciseSpec(null, "induction", null, null, goal, "n", null, null, null, null, definitions, null, null, null);
    }

    @Test
    void plainArithmeticGoalIsNotExtended() {
        var goal = MathNodes.eq(MathNodes.add(MathNodes.var("n"), MathNodes.num("0")), MathNodes.var("n"));
        var request = new GradeRequest("1.0", exercise(goal, null), null);
        assertThat(RegateVocabulary.usesExtendedVocabulary(request)).isFalse();
    }

    @Test
    void applyInTheGoalIsExtended() {
        var goal = MathNodes.eq(MathNodes.apply("fact_aux", MathNodes.var("x"), MathNodes.var("n")),
                MathNodes.mul(MathNodes.var("x"), MathNodes.apply("fact", MathNodes.var("n"))));
        var request = new GradeRequest("1.0", exercise(goal, null), null);
        assertThat(RegateVocabulary.usesExtendedVocabulary(request)).isTrue();
    }

    @Test
    void applyOnlyInDefinitionsIsExtended() {
        // The shim ships fact/fact_aux definitions globally, so even a non-apply goal travels with apply definitions
        // a 1.0 backend cannot parse — the gate must catch that.
        var goal = MathNodes.eq(MathNodes.add(MathNodes.var("n"), MathNodes.num("0")), MathNodes.var("n"));
        var definitions = List.of(new RuleSpec("fact_zero", "apply", MathNodes.apply("fact", MathNodes.num("0")), MathNodes.num("1"), false, null));
        var request = new GradeRequest("1.0", exercise(goal, definitions), null);
        assertThat(RegateVocabulary.usesExtendedVocabulary(request)).isTrue();
    }

    @Test
    void applyInAnInductionStepResultIsExtended() {
        var goal = MathNodes.eq(MathNodes.var("n"), MathNodes.var("n"));
        var stepResult = MathNodes.eq(MathNodes.apply("f", MathNodes.var("n")), MathNodes.var("n"));
        var submission = new SubmissionSpec(null, null, null, new Derivation(List.of()),
                new Derivation(List.of(new StepSpec("f_succ", List.of(0), "forward", "A", null, stepResult))));
        var request = new GradeRequest("1.0", exercise(goal, null), submission);
        assertThat(RegateVocabulary.usesExtendedVocabulary(request)).isTrue();
    }

    @Test
    void nullRequestIsNotExtended() {
        assertThat(RegateVocabulary.usesExtendedVocabulary(null)).isFalse();
    }
}
