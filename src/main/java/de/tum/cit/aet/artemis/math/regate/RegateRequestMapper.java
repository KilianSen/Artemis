package de.tum.cit.aet.artemis.math.regate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.math.domain.DerivationRole;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.GoalMode;
import de.tum.cit.aet.artemis.math.domain.InductionDatatype;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathProblemConfig;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;
import de.tum.cit.aet.artemis.math.domain.StepDirection;
import de.tum.cit.aet.artemis.math.domain.StepKind;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest.DatatypeSpec;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest.DatatypeSpec.ConstructorSpec;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest.DatatypeSpec.FieldSpec;
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
 * occurs. The rule catalogue travels inline (leanregate has no built-in catalogue), narrowed to the problem's
 * allowed rule subset if it has one. {@code definitions} are never narrowed: the protocol treats them as
 * definitional and always trusted, and the backends merge them into the citable rule table regardless of the
 * ruleset.
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
        // Narrowed to the problem's allowed rule subset; see RuleMapper#toRuleset for why this is defence in depth
        // (Artemis already rejected out-of-subset steps) and what a narrow subset costs the equivalence oracle.
        List<RuleSpec> ruleset = RuleMapper.toRuleset(blockRegistry, config.getAllowedRuleIds());
        // verify_rules left null: Artemis's catalogue is a trusted, code-reviewed contribution, so the backend takes the caller's warrant.
        OptionsSpec options = new OptionsSpec(false, null, true, null, null, config.isAcNormalization(), null);
        ExerciseSpec exercise = mode == GoalMode.EQUATION ? new ExerciseSpec(null, "equation", currentState, null, null, null, ruleset, null, null, null, null, options, null, null)
                : new ExerciseSpec(null, "transformation", currentState, config.getTargetExpression(), null, null, ruleset, null, null, null, null, options, null, null);
        // The backend derives the hint from the submitted state; a hint request has no steps, so carry the current
        // expression as the submission's `final` (an empty submission is rejected).
        return new GradeRequest(GradeRequest.PROTOCOL_VERSION, exercise, new SubmissionSpec(currentState, null, null, null, null));
    }

    private static ExerciseSpec toExercise(MathProblemConfig config, BlockRegistry blockRegistry, GoalMode mode) {
        // Narrowed to the problem's allowed rule subset (defence in depth — see RuleMapper#toRuleset).
        List<RuleSpec> ruleset = RuleMapper.toRuleset(blockRegistry, config.getAllowedRuleIds());
        // verify_rules left null: the catalogue is trusted upstream (see OptionsSpec); a per-submission re-proof would cost seconds of kernel time.
        OptionsSpec options = new OptionsSpec(config.isPartialCreditEnabled(), null, false, null, null, config.isAcNormalization(), null);
        return switch (mode) {
            case TRANSFORMATION -> new ExerciseSpec(null, "transformation", config.getSourceExpression(), config.getTargetExpression(), null, null, ruleset, null, null, null, null,
                    options, null, null);
            case EQUATION -> new ExerciseSpec(null, "equation", config.getGoalExpression(), null, null, null, ruleset, null, null, null, null, options, null, null);
            // Induction: goal P(v) + the variable inducted over + its datatype (absent ⇒ ℕ) + trusted recursive
            // definitions; catalogue rules travel inline too.
            case INDUCTION -> new ExerciseSpec(null, "induction", null, null, config.getGoalExpression(), config.getInductionVariable(), ruleset, null, null, null,
                    toDefinitions(blockRegistry, config.getGoalExpression()), options, inferInductionDomain(config.getGoalExpression()),
                    toDatatypeSpec(config.getInductionDatatype()));
        };
    }

    /**
     * Builds the {@code exercise.datatype} descriptor for a non-ℕ induction datatype (see {@code GRADING_PROTOCOL.md}
     * "Datatype induction"). {@link InductionDatatype#NAT ℕ} returns {@code null} — it keeps the legacy
     * {@code 0}/{@code succ} forms and travels without a descriptor. A field whose {@code sort} equals the datatype
     * name is a recursive position.
     *
     * @param datatype the problem's induction datatype
     * @return the wire descriptor, or {@code null} for ℕ
     */
    private static DatatypeSpec toDatatypeSpec(InductionDatatype datatype) {
        return switch (datatype == null ? InductionDatatype.NAT : datatype) {
            case NAT -> null;
            case LIST ->
                new DatatypeSpec("Lst", List.of(new ConstructorSpec("nil", List.of()), new ConstructorSpec("cons", List.of(new FieldSpec("h", "int"), new FieldSpec("t", "Lst")))));
            case TREE -> new DatatypeSpec("Tree", List.of(new ConstructorSpec("empty", List.of()),
                    new ConstructorSpec("node", List.of(new FieldSpec("l", "Tree"), new FieldSpec("v", "int"), new FieldSpec("r", "Tree")))));
        };
    }

    /**
     * TEMPORARY SHIM (A-M1): infers the numeric domain for an induction goal. A goal that applies recursive functions
     * ({@code apply} nodes — e.g. {@code fact_aux x n = x·fact n}) is integer-valued, so it must be certified over ℤ:
     * over the ℚ default cvc5 cannot discharge the leap and the submission degrades to review. Plain arithmetic
     * induction (no {@code apply}) keeps the ℚ default, so existing exercises are unaffected. A {@code frac} anywhere
     * forces ℚ, since a fraction goal is genuinely rational.
     * <p>
     * Replace with an instructor-authored per-problem {@code domain} (bundled with per-problem definitions, plan
     * option b): domain is exercise intent, not something to guess from the goal's shape.
     *
     * @param goal the induction goal {@code P(n)}
     * @return {@code "int"} for an {@code apply}-using, {@code frac}-free goal, else {@code null} (ℚ default)
     */
    private static String inferInductionDomain(MathNode goal) {
        return containsType(goal, "apply") && !containsType(goal, "frac") ? "int" : null;
    }

    /** @return {@code true} if any node in {@code tree} has the given {@code type}. */
    private static boolean containsType(MathNode tree, String type) {
        if (tree == null) {
            return false;
        }
        if (type.equals(tree.getType())) {
            return true;
        }
        if (tree.getSlots() == null) {
            return false;
        }
        return tree.getSlots().values().stream().flatMap(List::stream).anyMatch(child -> containsType(child, type));
    }

    private static List<RuleSpec> toDefinitions(BlockRegistry blockRegistry, MathNode goal) {
        List<RewriteRule> all = blockRegistry.getAllDefinitions();
        if (all.isEmpty()) {
            return null;
        }
        List<RewriteRule> relevant = reachableDefinitions(goal, all);
        return relevant.isEmpty() ? null : relevant.stream().map(d -> RuleMapper.toRuleSpec(d, "definition")).toList();
    }

    /**
     * Keeps only the definitions whose function is reachable from the goal — the functions named in the goal, then
     * whatever those functions' definitions transitively reference. This keeps each induction request
     * self-consistent: a tree goal never ships list definitions (whose {@code cons}/{@code nil} constructors the tree
     * datatype does not declare), which the backend would fail to translate.
     * <p>
     * Needed only because the definitions are contributed globally by a shim ({@link de.tum.cit.aet.artemis.math.domain.blocks.ApplyBlockDefinition});
     * per-problem authored definitions would ship exactly the exercise's functions and make this filtering moot.
     *
     * @param goal the induction goal (its {@code apply} function names seed the reachable set)
     * @param all  all code-contributed definitions
     * @return the subset reachable from the goal (all of them if {@code goal} is {@code null})
     */
    private static List<RewriteRule> reachableDefinitions(MathNode goal, List<RewriteRule> all) {
        if (goal == null) {
            return all;
        }
        Map<String, List<RewriteRule>> byFunction = new HashMap<>();
        for (RewriteRule definition : all) {
            String name = definedFunctionName(definition);
            if (name != null) {
                byFunction.computeIfAbsent(name, key -> new ArrayList<>()).add(definition);
            }
        }
        Set<String> reached = new HashSet<>();
        Deque<String> worklist = new ArrayDeque<>(operatorNames(goal));
        while (!worklist.isEmpty()) {
            String name = worklist.poll();
            if (!reached.add(name)) {
                continue;
            }
            for (RewriteRule definition : byFunction.getOrDefault(name, List.of())) {
                worklist.addAll(operatorNames(definition.pattern()));
                worklist.addAll(operatorNames(definition.template()));
            }
        }
        return all.stream().filter(definition -> reached.contains(definedFunctionName(definition))).toList();
    }

    /**
     * @return the operator a definition defines — the outer {@code apply} value of its pattern (a named function like
     *         {@code fact}/{@code sum}), or the outer node type (a code-contributed operator like {@code pow}) — or
     *         {@code null} for a value-less pattern
     */
    private static String definedFunctionName(RewriteRule definition) {
        return operatorName(definition.pattern());
    }

    /** @return the operator name of a single node: its {@code apply} value, else (for a non-terminal) its node type. */
    private static String operatorName(MathNode node) {
        if (node == null) {
            return null;
        }
        if ("apply".equals(node.getType())) {
            return node.getValue();
        }
        return node.getSlots() != null && !node.getSlots().isEmpty() ? node.getType() : null;
    }

    /** @return every operator name referenced anywhere in the tree — {@code apply} functions/constructors and non-terminal node types. */
    private static Set<String> operatorNames(MathNode node) {
        Set<String> names = new HashSet<>();
        collectOperatorNames(node, names);
        return names;
    }

    private static void collectOperatorNames(MathNode node, Set<String> out) {
        if (node == null) {
            return;
        }
        String name = operatorName(node);
        if (name != null) {
            out.add(name);
        }
        if (node.getSlots() != null) {
            for (List<MathNode> children : node.getSlots().values()) {
                for (MathNode child : children) {
                    collectOperatorNames(child, out);
                }
            }
        }
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
