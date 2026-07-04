import { DerivationStep } from './derivation-step.model';

/**
 * A student's answer to a single {@link MathProblem}. Mirrors the server {@code MathProblemAnswerDTO}.
 */
export class MathProblemAnswer {
    public id?: number;
    public problemId?: number;
    /** Points earned on this problem (response only, populated after grading). */
    public scoreInPoints?: number;
    public steps?: DerivationStep[];

    constructor(problemId?: number) {
        this.problemId = problemId;
        this.steps = [];
    }
}
