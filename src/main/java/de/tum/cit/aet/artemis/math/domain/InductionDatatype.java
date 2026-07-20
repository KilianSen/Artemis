package de.tum.cit.aet.artemis.math.domain;

/**
 * The inductive datatype an INDUCTION-mode problem's induction variable ranges over (see
 * {@code GRADING_PROTOCOL.md} "Datatype induction", protocol 1.1). Determines the base/step constructors the
 * induction workspace generates and the {@code exercise.datatype} descriptor sent to the grading backend.
 * <p>
 * Deliberately a small closed set — ℕ, lists, binary trees — matching the backend's "one base + one recursive
 * constructor (up to two recursive fields)" support, not a general datatype engine.
 */
public enum InductionDatatype {

    /** ℕ: base {@code 0}, step {@code S n}. The legacy default — no datatype descriptor travels on the wire. */
    NAT,

    /** {@code Lst = nil | cons(h: int, t: Lst)}: structural induction over a list, one IH {@code P(t)}. */
    LIST,

    /** {@code Tree = empty | node(l: Tree, v: int, r: Tree)}: structural induction over a binary tree, two IHs {@code P(l)}, {@code P(r)}. */
    TREE
}
