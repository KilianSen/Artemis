package de.tum.cit.aet.artemis.math.grader;

import de.tum.cit.aet.artemis.math.service.MathGradingService;

/**
 * How much a grader's verdict can be trusted, used to arbitrate between several backends grading the same problem.
 * <p>
 * The tiers are ordered from weakest to strongest ({@link Enum#compareTo} follows declaration order). Strength captures
 * how deeply a grader can <em>reason</em>, which matters only for <em>rejections</em>: a <b>pass</b> from any tier is
 * sound (the grader exhibited a valid derivation/proof), but a conclusive <b>fail</b> from a weaker grader is not
 * trustworthy when a stronger grader was unable to decide — the answer may rely on reasoning the weaker grader cannot
 * express. {@link MathGradingService} uses this to avoid letting a weak grader's fail override a stronger grader that
 * abstained, routing such cases to review instead.
 */
public enum GraderStrength {

    /** Replays the student's steps against the fixed rewrite-rule library; no semantic reasoning. The path checker. */
    STRUCTURAL,

    /** Equality saturation over an e-graph; decides semantic equality without a kernel-checked certificate. Eggregate. */
    SEMANTIC,

    /** Kernel-checked or SMT-backed formal proof (Lean / Rocq / cvc5). The strongest, and the only tier that certifies induction. */
    FORMAL
}
