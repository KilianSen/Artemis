import { MathNode } from './math-node.model';
import { DerivationStep } from './derivation-step.model';
import { DEFAULT_GRADER_TYPE, GraderType } from './grader-type.model';
import { DEFAULT_GOAL_MODE, GoalMode } from './goal-mode.model';

/**
 * A single math problem (question) held by a {@link MathExercise}. Mirrors the server {@code MathProblemDTO}.
 * All per-problem configuration that formerly lived on the exercise now lives here.
 */
export class MathProblem {
    public id?: number;
    public title?: string;
    public points?: number;
    public sourceExpression?: MathNode;
    public targetExpression?: MathNode;
    /** Single goal tree (typically an equality) for EQUATION mode. Unused in TRANSFORMATION mode. */
    public goalExpression?: MathNode;
    /** How the goal is encoded — source→target or single equation closed by tautology. */
    public goalMode?: GoalMode = DEFAULT_GOAL_MODE;
    /** Backend grader to dispatch to. Defaults to REWRITE_CHAIN. */
    public graderType?: GraderType = DEFAULT_GRADER_TYPE;
    public partialCreditEnabled?: boolean;
    /** When true the grader treats {@code +} and {@code ·} as commutative/associative for equality comparisons. */
    public acNormalization?: boolean;
    public onlyShowApplicableRules?: boolean;
    public allowVerification?: boolean;
    public manualDerivation?: boolean;
    /** The instructor-supplied worked derivation for this problem (an ordered list of steps). */
    public exampleDerivations?: DerivationStep[];

    constructor() {
        this.points = 1;
        this.goalMode = DEFAULT_GOAL_MODE;
        this.graderType = DEFAULT_GRADER_TYPE;
        this.allowVerification = true;
        this.exampleDerivations = [];
    }
}
