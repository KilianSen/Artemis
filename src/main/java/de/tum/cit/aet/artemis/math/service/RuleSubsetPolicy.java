package de.tum.cit.aet.artemis.math.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathProblemConfig;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;
import de.tum.cit.aet.artemis.math.domain.StepKind;

/**
 * The single interpretation of a problem's {@link MathProblemConfig#getAllowedRuleIds() allowed rule subset}.
 * <p>
 * <b>Why this is enforced here and not by the grading backends.</b> Shipping the subset in {@code exercise.ruleset}
 * and letting a backend police it would be an exploit, not an enforcement: eggregate answers a step citing a rule
 * absent from the ruleset with an HTTP 400, and the cvc5/coq/lean backends answer {@code unknown} — and Artemis maps
 * every one of those to an inconclusive verdict, i.e. <em>tutor review</em>. Citing a forbidden rule would then be a
 * strict upgrade over a zero. That protocol behaviour is deliberate, not a bug: {@code GRADING_PROTOCOL.md} states a
 * step citing an unprovable rule grades {@code unknown} and never {@code invalid_derivation}, because the backend has
 * to assume the student was handed what they cited. Only the host knows which rules the instructor actually handed
 * out, so only the host can call it cheating. The subset still travels to the backends (see
 * {@code RuleMapper#toRuleset}) so they reason under the same rules — as defence in depth, never as the check.
 * <p>
 * <b>Two exemptions, both load-bearing.</b>
 * <ul>
 * <li>Recursive <em>definitions</em> ({@code pow_succ}, {@code fact_zero}, …) are never restricted. The protocol
 * treats them as definitional and therefore always trusted in every mode, and the backends merge them into the
 * citable rule table; restricting them would break every induction submission.</li>
 * <li>Kind-B (Leibniz) steps are never checked. They carry fabricated ids the client mints for the induction
 * hypothesis ({@code induction_hypothesis}, {@code induction_hypothesis_<field>}) that exist in no registry, so any
 * id check would reject every inductive step. Their soundness comes from the substituted equation the backend
 * re-checks, not from a rule id.</li>
 * </ul>
 * A rule id that is neither in the subset nor a definition — including a {@code null} id and a dangling id left over
 * from a renamed rule — is <em>not allowed</em>. Rule ids are code identifiers with no stability guarantee, so
 * "unknown" must never degrade to "allowed by default".
 */
@Conditional(MathEnabled.class)
@Lazy
@Service
public class RuleSubsetPolicy {

    private final BlockRegistry blockRegistry;

    /** Lazily derived once: definition ids are code-contributed and fixed for the JVM's lifetime. */
    private volatile Set<String> definitionRuleIds;

    /** Lazily derived once: every id an instructor may legitimately name (catalogue rules plus definitions). */
    private volatile Set<String> knownRuleIds;

    public RuleSubsetPolicy(BlockRegistry blockRegistry) {
        this.blockRegistry = blockRegistry;
    }

    /**
     * @param config the problem configuration to inspect (may be {@code null})
     * @return {@code true} when the problem restricts nothing, i.e. every catalogue rule stays citable
     */
    public static boolean isUnrestricted(MathProblemConfig config) {
        List<String> allowed = config == null ? null : config.getAllowedRuleIds();
        return allowed == null || allowed.isEmpty();
    }

    /**
     * Whether a rule id may be cited under the problem's subset.
     *
     * @param config the problem configuration being graded
     * @param ruleId the cited rule id (may be {@code null})
     * @return {@code true} if the problem is unrestricted, the id is a trusted definition, or the id is in the subset
     */
    public boolean isRuleAllowed(MathProblemConfig config, String ruleId) {
        return isUnrestricted(config) || isRuleAllowed(config.getAllowedRuleIds(), ruleId);
    }

    /**
     * Whether a rule id may be cited under an explicit subset. Used at save time, where only the incoming DTO's list
     * exists yet.
     *
     * @param allowedRuleIds the subset ({@code null}/empty means unrestricted)
     * @param ruleId         the cited rule id (may be {@code null})
     * @return {@code true} if the subset is unrestricted, the id is a trusted definition, or the id is in the subset
     */
    public boolean isRuleAllowed(Collection<String> allowedRuleIds, String ruleId) {
        if (allowedRuleIds == null || allowedRuleIds.isEmpty()) {
            return true;
        }
        // A step with no rule id names nothing and is therefore in no subset. Checked before any lookup: both the
        // derived definition set and a List.of(...) subset are null-hostile, so probing them with null would throw.
        if (ruleId == null) {
            return false;
        }
        // Definitions are definitional and therefore always trusted (GRADING_PROTOCOL.md); they are outside the subset's reach.
        return definitionRuleIds().contains(ruleId) || allowedRuleIds.contains(ruleId);
    }

    /**
     * Whether a submitted step is allowed under the problem's subset. Only {@link StepKind#A} steps are checked —
     * kind-B steps carry fabricated hypothesis ids that exist in no registry.
     *
     * @param config the problem configuration being graded
     * @param step   the submitted step
     * @return {@code true} if the step may be replayed
     */
    public boolean isStepAllowed(MathProblemConfig config, DerivationStep step) {
        if (step == null || step.getKind() != StepKind.A) {
            return true;
        }
        return isRuleAllowed(config, step.getAppliedRuleId());
    }

    /**
     * Finds the first step that cites a rule the instructor switched off.
     *
     * @param config the problem configuration being graded
     * @param steps  the student's ordered derivation steps
     * @return the position in {@code steps} of the first disallowed step, or empty when every step is allowed
     */
    public OptionalInt firstDisallowedStepIndex(MathProblemConfig config, List<DerivationStep> steps) {
        if (steps == null || steps.isEmpty() || isUnrestricted(config)) {
            return OptionalInt.empty();
        }
        for (int i = 0; i < steps.size(); i++) {
            if (!isStepAllowed(config, steps.get(i))) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Every rule id an instructor may legitimately put in a subset: the catalogue rules plus the trusted recursive
     * definitions. Used to reject a typo'd or dangling id with a 400 at create/import time.
     *
     * @return the known rule ids
     */
    public Set<String> knownRuleIds() {
        Set<String> cached = knownRuleIds;
        if (cached == null) {
            Set<String> ids = new HashSet<>(definitionRuleIds());
            for (var block : blockRegistry.getAllBlocks()) {
                for (RewriteRule rule : blockRegistry.getNormalizedRulesFor(block)) {
                    ids.add(rule.id());
                }
            }
            cached = Set.copyOf(ids);
            knownRuleIds = cached;
        }
        return cached;
    }

    /** The ids of the code-contributed recursive definitions, which the subset never restricts. */
    private Set<String> definitionRuleIds() {
        Set<String> cached = definitionRuleIds;
        if (cached == null) {
            cached = blockRegistry.getAllDefinitions().stream().map(RewriteRule::id).collect(Collectors.toUnmodifiableSet());
            definitionRuleIds = cached;
        }
        return cached;
    }
}
