package de.tum.cit.aet.artemis.math.regate;

import java.util.List;
import java.util.Set;

import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest;
import de.tum.cit.aet.artemis.math.regate.dto.RuleSpec;
import de.tum.cit.aet.artemis.math.regate.dto.StepSpec;

/**
 * Detects whether a {@link GradeRequest} carries node types introduced in Regate protocol {@code 1.1} — the
 * "extended vocabulary". A backend that only implements {@code 1.0} rejects such a request as malformed
 * ({@code HTTP 400}, per {@code GRADING_PROTOCOL.md} "Unimplemented vocabulary"), so
 * {@link AbstractRegateGrader} uses this to turn that 400 into an <em>instructor-facing configuration error</em>
 * (backend too old for the exercise) routed to review, never a student-facing zero.
 */
public final class RegateVocabulary {

    /**
     * MathNode types added in protocol {@code 1.1}. Grows as capabilities land: {@code apply} (C1, function
     * application) now; the list ({@code nil}/{@code cons}) and tree ({@code node}/{@code empty}) constructors
     * with C3/C4.
     */
    private static final Set<String> EXTENDED_TYPES = Set.of("apply");

    private RegateVocabulary() {
    }

    /**
     * @param request the assembled grade request
     * @return {@code true} if any MathNode anywhere in the request (exercise goal/source/target, ruleset and
     *         definition patterns, or the submission's steps) uses an {@link #EXTENDED_TYPES extended} node type
     */
    public static boolean usesExtendedVocabulary(GradeRequest request) {
        if (request == null) {
            return false;
        }
        return exerciseUsesExtended(request.exercise()) || submissionUsesExtended(request.submission());
    }

    private static boolean exerciseUsesExtended(GradeRequest.ExerciseSpec exercise) {
        if (exercise == null) {
            return false;
        }
        return containsExtended(exercise.source()) || containsExtended(exercise.target()) || containsExtended(exercise.goal()) || anyNodeExtended(exercise.reference())
                || anyNodeExtended(exercise.hypotheses()) || anyRuleExtended(exercise.ruleset()) || anyRuleExtended(exercise.definitions());
    }

    private static boolean submissionUsesExtended(GradeRequest.SubmissionSpec submission) {
        if (submission == null) {
            return false;
        }
        return containsExtended(submission.finalExpression()) || anyStepExtended(submission.steps()) || (submission.base() != null && anyStepExtended(submission.base().steps()))
                || (submission.step() != null && anyStepExtended(submission.step().steps()));
    }

    private static boolean anyRuleExtended(List<RuleSpec> rules) {
        return rules != null && rules.stream().anyMatch(rule -> containsExtended(rule.lhs()) || containsExtended(rule.rhs()));
    }

    private static boolean anyStepExtended(List<StepSpec> steps) {
        return steps != null && steps.stream().anyMatch(step -> containsExtended(step.result()) || anyNodeExtended(step.equation()));
    }

    private static boolean anyNodeExtended(List<MathNode> nodes) {
        return nodes != null && nodes.stream().anyMatch(RegateVocabulary::containsExtended);
    }

    private static boolean containsExtended(MathNode node) {
        if (node == null) {
            return false;
        }
        if (EXTENDED_TYPES.contains(node.getType())) {
            return true;
        }
        if (node.getSlots() == null) {
            return false;
        }
        for (List<MathNode> children : node.getSlots().values()) {
            for (MathNode child : children) {
                if (containsExtended(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
