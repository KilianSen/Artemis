package de.tum.cit.aet.artemis.math.grader;

/**
 * Latency class of a {@link GraderType}, used to route asynchronous grading onto a fast or slow lane so a
 * multi-second formal proof never starves interactive grading (Phase 2).
 */
public enum GradingSpeed {

    /** Sub-second / interactive: in-process rewriting, eggregate e-graph, cvc5 SMT. */
    FAST,

    /** Formal proof search that may take seconds: leanregate, coqregate. */
    SLOW
}
