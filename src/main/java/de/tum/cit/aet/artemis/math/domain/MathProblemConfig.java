package de.tum.cit.aet.artemis.math.domain;

import de.tum.cit.aet.artemis.math.grader.GraderType;

/**
 * The per-problem configuration a {@link de.tum.cit.aet.artemis.math.grader.MathGrader} needs to grade a single math
 * derivation, decoupled from where that configuration lives.
 * <p>
 * A standalone {@link MathExercise} carries exactly one such configuration, while a {@code MathProblem} inside a
 * multiplex exercise carries one per problem. Keeping the grader's contract on this interface (rather than on the
 * concrete {@code MathExercise}) lets a single grading engine serve both shapes and keeps the grader independent of
 * the upcoming exercise-format / grader migration.
 */
public interface MathProblemConfig {

    /**
     * @return the starting expression for TRANSFORMATION mode (the student rewrites it toward {@link #getTargetExpression()})
     */
    MathNode getSourceExpression();

    /**
     * @return the goal expression for TRANSFORMATION mode
     */
    MathNode getTargetExpression();

    /**
     * @return the equation to prove for EQUATION mode, or the property {@code P(n)} to prove for INDUCTION mode
     */
    MathNode getGoalExpression();

    /**
     * @return the induction variable (the ℕ variable inducted over) for INDUCTION mode, else {@code null}
     */
    String getInductionVariable();

    /**
     * @return the goal mode selecting how the derivation is graded (TRANSFORMATION vs EQUATION)
     */
    GoalMode getGoalMode();

    /**
     * @return the grader backend to dispatch to
     */
    GraderType getGraderType();

    /**
     * @return the optional slow formal certifier that upgrades the primary grader's preliminary verdict, or {@code null} for single-backend grading
     */
    default GraderType getCertifyingGraderType() {
        return null;
    }

    /**
     * @return whether distance-based partial credit is awarded when the target is not reached
     */
    boolean isPartialCreditEnabled();

    /**
     * @return whether equality and visited-state checks are AC-normalised (commutativity / associativity handled implicitly)
     */
    boolean isAcNormalization();
}
