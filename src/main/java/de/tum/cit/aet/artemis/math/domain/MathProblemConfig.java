package de.tum.cit.aet.artemis.math.domain;

import java.util.List;

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
     * @return the induction variable (the variable inducted over) for INDUCTION mode, else {@code null}
     */
    String getInductionVariable();

    /**
     * @return the datatype the induction variable ranges over for INDUCTION mode; defaults to {@link InductionDatatype#NAT ℕ}
     */
    default InductionDatatype getInductionDatatype() {
        return InductionDatatype.NAT;
    }

    /**
     * @return the goal mode selecting how the derivation is graded (TRANSFORMATION vs EQUATION)
     */
    GoalMode getGoalMode();

    /**
     * @return the grader backends to dispatch to, in preference order; never empty
     */
    List<GraderType> getGraderTypes();

    /**
     * @return the primary grader backend: the first configured grader, or {@link GraderType#PATH_CHECKER} when none is
     *         configured. Convenience for the single-grader paths (hints, reachability, rule application).
     */
    default GraderType getGraderType() {
        List<GraderType> types = getGraderTypes();
        return types == null || types.isEmpty() ? GraderType.PATH_CHECKER : types.getFirst();
    }

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

    /**
     * The rule subset the student is restricted to, by rule id.
     * <p>
     * {@code null} or empty means <em>unrestricted</em> (every catalogue rule is citable) — the default, and what
     * every configuration that never sets it keeps. Recursive definitions and kind-B (Leibniz / induction-hypothesis)
     * steps are outside this list's reach; see
     * {@link de.tum.cit.aet.artemis.math.service.RuleSubsetPolicy RuleSubsetPolicy}, which owns the interpretation.
     *
     * @return the allowed rule ids, or an empty list when the problem restricts nothing
     */
    default List<String> getAllowedRuleIds() {
        return List.of();
    }
}
