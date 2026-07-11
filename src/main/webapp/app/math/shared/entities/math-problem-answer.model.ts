import { DerivationStep } from './derivation-step.model';

/**
 * A student's answer to a single {@link MathProblem}. Mirrors the server {@code MathProblemAnswerDTO}.
 */
export class MathProblemAnswer {
    public id?: number;
    public problemId?: number;
    /** Points earned on this problem (response only, populated after grading). */
    public scoreInPoints?: number;
    /** Backend verdict category (e.g. PROVEN_EQUAL), response only. */
    public gradingOutcome?: string;
    /** Whether the verdict is backed by a re-checked proof, response only. */
    public certified?: boolean;
    /** Human-readable grader feedback, response only. */
    public gradingFeedback?: string;
    /** A counterexample assignment for a disproof verdict, response only. */
    public witness?: string;
    /** True while a slow certifier is still to upgrade the fast preliminary verdict (Phase 2b, response only). */
    public certificationPending?: boolean;
    public steps?: DerivationStep[];

    constructor(problemId?: number) {
        this.problemId = problemId;
        this.steps = [];
    }
}
