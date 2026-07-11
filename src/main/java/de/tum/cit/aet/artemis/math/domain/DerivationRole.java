package de.tum.cit.aet.artemis.math.domain;

/**
 * Which derivation of a {@link MathProblemAnswer} a {@link DerivationStep} belongs to.
 * <p>
 * {@link #MAIN} is the single derivation of a transformation/equation answer. {@link #BASE} and
 * {@link #STEP} are the two obligations of an induction proof: the base case {@code P(0)} and the inductive
 * step (proving {@code P(S n)} with the hypothesis {@code P(n)} in scope). The steps of all derivations live
 * in one ordered list on the answer, partitioned by this role.
 */
public enum DerivationRole {

    /** The single derivation of a transformation/equation answer. */
    MAIN,

    /** The base-case derivation of an induction proof ({@code P(0)}). */
    BASE,

    /** The inductive-step derivation of an induction proof ({@code P(n) ⊢ P(S n)}). */
    STEP
}
