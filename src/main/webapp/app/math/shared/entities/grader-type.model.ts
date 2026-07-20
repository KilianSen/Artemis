import { GoalMode } from './goal-mode.model';

/**
 * Mirror of the backend {@code GraderType} enum.
 * Discriminator used to dispatch a math grader for a given problem.
 */
export type GraderType = 'PATH_CHECKER' | 'EGGREGATE' | 'LEANREGATE' | 'COQREGATE' | 'CVC5REGATE';

export const DEFAULT_GRADER_TYPE: GraderType = 'PATH_CHECKER';

/** Display label for each grader type, used in the editor dropdown. */
export const GRADER_TYPE_LABELS: Record<GraderType, string> = {
    PATH_CHECKER: 'Path checker (step-by-step, in-process)',
    EGGREGATE: 'Regate · eggregate (e-graph)',
    LEANREGATE: 'Regate · leanregate (Lean formal)',
    COQREGATE: 'Regate · coqregate (Coq, induction)',
    CVC5REGATE: 'Regate · cvc5regate (SMT, induction)',
};

/** Which goal modes each grader can grade conclusively — mirrors the server {@code GraderType.supports(mode)}. */
export const GRADER_MODE_SUPPORT: Record<GraderType, GoalMode[]> = {
    PATH_CHECKER: ['TRANSFORMATION', 'EQUATION'],
    EGGREGATE: ['TRANSFORMATION', 'EQUATION'],
    LEANREGATE: ['TRANSFORMATION', 'EQUATION', 'INDUCTION'],
    COQREGATE: ['INDUCTION'],
    CVC5REGATE: ['INDUCTION'],
};

/** Whether a grader can grade a given goal mode conclusively. */
export function graderSupportsMode(grader: GraderType, mode: GoalMode): boolean {
    return GRADER_MODE_SUPPORT[grader].includes(mode);
}

/** A sensible default grader for a goal mode: the in-process engine for transformation/equation, leanregate for induction. */
export function defaultGraderForMode(mode: GoalMode): GraderType {
    return mode === 'INDUCTION' ? 'LEANREGATE' : DEFAULT_GRADER_TYPE;
}

/** A sensible default grader selection (a fresh single-element list) for a goal mode. */
export function defaultGradersForMode(mode: GoalMode): GraderType[] {
    return [defaultGraderForMode(mode)];
}
