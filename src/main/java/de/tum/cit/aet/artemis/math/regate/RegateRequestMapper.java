package de.tum.cit.aet.artemis.math.regate;

import java.util.List;

import de.tum.cit.aet.artemis.math.domain.DerivationRole;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.GoalMode;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathProblemConfig;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;
import de.tum.cit.aet.artemis.math.domain.StepDirection;
import de.tum.cit.aet.artemis.math.domain.StepKind;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest.Derivation;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest.ExerciseSpec;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest.OptionsSpec;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest.SubmissionSpec;
import de.tum.cit.aet.artemis.math.regate.dto.RuleSpec;
import de.tum.cit.aet.artemis.math.regate.dto.StepSpec;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/**
 * Assembles a Regate {@link GradeRequest} from a {@link MathProblemConfig} and the student's derivation.
 * Pure structural assembly — MathNodes are already in the protocol vocabulary, so no value translation
 * occurs. The whole rule catalogue travels inline (leanregate has no built-in catalogue).
 * <p>
 * Mode mapping mirrors the conformance fixtures: {@code transformation} carries {@code source}/{@code target};
 * {@code equation} carries the equality tree as {@code source} with a null {@code target} (completion is
 * reduction to a tautology); {@code induction} carries the goal {@code P(n)}, the induction variable, and the
 * trusted recursive {@code definitions}, with the submission split into base ({@code P(0)}) and step
 * ({@code P(S n)}) derivations by {@link DerivationRole}.
 */
public final class RegateRequestMapper {

    private RegateRequestMapper() {
    }

    /**
     * Assembles a {@link GradeRequest} from a problem configuration, the student's derivation, and the rule
     * catalogue (which travels inline).
     *
     * @param config        the problem configuration being graded
     * @param steps         the student's ordered derivation steps
     * @param blockRegistry the source of the rule catalogue
     * @return the assembled grade request
     */
    public static GradeRequest toRequest(MathProblemConfig config, List<DerivationStep> steps, BlockRegistry blockRegistry) {
        GoalMode mode = config.getGoalMode() == null ? GoalMode.TRANSFORMATION : config.getGoalMode();
        SubmissionSpec submission = mode == GoalMode.INDUCTION ? toInductionSubmission(steps) : toSubmission(steps);
        return new GradeRequest(GradeRequest.PROTOCOL_VERSION, toExercise(config, blockRegistry, mode), submission);
    }

    /**
     * Assembles a hint request: the backend is asked (via {@code want_hint}) for the next rule to apply from
     * {@code currentState} toward the goal, with no submitted steps. Returns {@code null} for induction, which
     * has no single-rule next-step hint.
     *
     * @param config        the problem configuration
     * @param currentState  the student's current expression
     * @param blockRegistry the source of the rule catalogue
     * @return a hint grade request, or {@code null} if hints do not apply to this mode
     */
    public static GradeRequest toHintRequest(MathProblemConfig config, MathNode currentState, BlockRegistry blockRegistry) {
        GoalMode mode = config.getGoalMode() == null ? GoalMode.TRANSFORMATION : config.getGoalMode();
        if (mode == GoalMode.INDUCTION) {
            return null;
        }
        List<RuleSpec> ruleset = RuleMapper.toRuleset(blockRegistry);
        // verify_rules left null: Artemis's catalogue is a trusted, code-reviewed contribution, so the backend takes the caller's warrant.
        OptionsSpec options = new OptionsSpec(false, null, true, null, null, config.isAcNormalization(), null);
        ExerciseSpec exercise = mode == GoalMode.EQUATION ? new ExerciseSpec(null, "equation", currentState, null, null, null, ruleset, null, null, null, null, options)
                : new ExerciseSpec(null, "transformation", currentState, config.getTargetExpression(), null, null, ruleset, null, null, null, null, options);
        // The backend derives the hint from the submitted state; a hint request has no steps, so carry the current
        // expression as the submission's `final` (an empty submission is rejected).
        return new GradeRequest(GradeRequest.PROTOCOL_VERSION, exercise, new SubmissionSpec(currentState, null, null, null, null));
    }

    private static ExerciseSpec toExercise(MathProblemConfig config, BlockRegistry blockRegistry, GoalMode mode) {
        List<RuleSpec> ruleset = RuleMapper.toRuleset(blockRegistry);
        // verify_rules left null: the catalogue is trusted upstream (see OptionsSpec); a per-submission re-proof would cost seconds of kernel time.
        OptionsSpec options = new OptionsSpec(config.isPartialCreditEnabled(), null, false, null, null, config.isAcNormalization(), null);
        return switch (mode) {
            case TRANSFORMATION ->
                new ExerciseSpec(null, "transformation", config.getSourceExpression(), config.getTargetExpression(), null, null, ruleset, null, null, null, null, options);
            case EQUATION -> new ExerciseSpec(null, "equation", config.getGoalExpression(), null, null, null, ruleset, null, null, null, null, options);
            // Induction: goal P(n) + the ℕ variable inducted over + trusted recursive definitions; catalogue rules travel inline too.
            case INDUCTION -> new ExerciseSpec(null, "induction", null, null, config.getGoalExpression(), config.getInductionVariable(), ruleset, null, null, null,
                    toDefinitions(blockRegistry), options);
        };
    }

    private static List<RuleSpec> toDefinitions(BlockRegistry blockRegistry) {
        List<RewriteRule> definitions = blockRegistry.getAllDefinitions();
        if (definitions.isEmpty()) {
            return null;
        }
        return definitions.stream().map(d -> RuleMapper.toRuleSpec(d, "definition")).toList();
    }

    private static SubmissionSpec toSubmission(List<DerivationStep> steps) {
        List<StepSpec> stepSpecs = steps.stream().map(RegateRequestMapper::toStep).toList();
        return new SubmissionSpec(null, stepSpecs, null, null, null);
    }

    /** Splits the answer's flat step list into the induction base ({@code P(0)}) and step ({@code P(S n)}) derivations by {@link DerivationRole}. */
    private static SubmissionSpec toInductionSubmission(List<DerivationStep> steps) {
        List<StepSpec> base = steps.stream().filter(s -> s.getDerivationRole() == DerivationRole.BASE).map(RegateRequestMapper::toStep).toList();
        List<StepSpec> step = steps.stream().filter(s -> s.getDerivationRole() == DerivationRole.STEP).map(RegateRequestMapper::toStep).toList();
        return new SubmissionSpec(null, null, null, new Derivation(base), new Derivation(step));
    }

    private static StepSpec toStep(DerivationStep step) {
        // An empty path targets the root and must be transmitted (StepSpec is NON_NULL, so [] survives; a dropped
        // path deserializes to null). A kind-B step carries the substituted equality (e.g. the induction hypothesis).
        List<Integer> path = step.getTargetNodePath() == null ? List.of() : step.getTargetNodePath();
        String direction = step.getDirection() == StepDirection.REVERSE ? "reverse" : "forward";
        boolean leibniz = step.getKind() == StepKind.B;
        List<MathNode> equation = leibniz ? equationSides(step.getSubstitutionEquation()) : null;
        return new StepSpec(step.getAppliedRuleId(), path, direction, leibniz ? "B" : "A", equation, step.getResultExpression());
    }

    /** Extracts {@code [left, right]} from an equality MathNode for a kind-B step's {@code equation}, or {@code null}. */
    private static List<MathNode> equationSides(MathNode equation) {
        if (equation == null || equation.getSlots() == null) {
            return null;
        }
        List<MathNode> left = equation.getSlots().get("left");
        List<MathNode> right = equation.getSlots().get("right");
        if (left == null || left.isEmpty() || right == null || right.isEmpty()) {
            return null;
        }
        return List.of(left.getFirst(), right.getFirst());
    }
}
