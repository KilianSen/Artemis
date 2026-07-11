package de.tum.cit.aet.artemis.math.domain;

/**
 * How the goal of a math exercise is encoded.
 * <p>
 * {@link #TRANSFORMATION} is the legacy mode: source and target are two separate trees and the
 * student transforms source into target step by step. {@link #EQUATION} encodes the goal as a single
 * tree — typically an {@code equality(LHS, RHS)} — and completion is reduction to a tautology
 * (a tree where both sides of the equality are structurally equal). {@link #INDUCTION} proves
 * {@code ∀n. P(n)} over ℕ via separate base and step derivations; it is graded only by a Regate backend
 * with a proof kernel (see {@link de.tum.cit.aet.artemis.math.grader.GraderType}).
 */
public enum GoalMode {

    /** Legacy shape: source-to-target derivation. */
    TRANSFORMATION,

    /** Single goal tree (typically an equality); math is complete when {@link MathNodes#isTautology(MathNode)} holds. */
    EQUATION,

    /** Induction over ℕ: a goal {@code P(n)}, an induction variable, and base + step derivations. */
    INDUCTION
}
