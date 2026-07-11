package de.tum.cit.aet.artemis.math.domain;

/**
 * How a {@link DerivationStep} is justified (see {@code GRADING_PROTOCOL.md} {@code submission.steps.kind}).
 */
public enum StepKind {

    /** Rule application: rewrite by a catalogue rule or a recursive definition. */
    A,

    /**
     * Leibniz substitution: substitute equals-for-equals using a known equality (a hypothesis), e.g. applying
     * the induction hypothesis {@code P(n)} in the inductive step. The equality travels in
     * {@link DerivationStep#getSubstitutionEquation()}.
     */
    B
}
