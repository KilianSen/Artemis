import { MathNode } from './math-node.model';
import { DerivationStep } from './derivation-step.model';
import { DEFAULT_GRADER_TYPE, GraderType } from './grader-type.model';
import { DEFAULT_GOAL_MODE, GoalMode } from './goal-mode.model';

/** The inductive datatype an INDUCTION problem inducts over. Mirrors the server {@code InductionDatatype} enum. */
export type InductionDatatype = 'NAT' | 'LIST' | 'TREE';

/** Display labels for the datatype picker in the induction authoring form. */
export const INDUCTION_DATATYPE_LABELS: Record<InductionDatatype, string> = {
    NAT: 'Natural numbers ℕ (0 / S n)',
    LIST: 'List (nil / cons h t)',
    TREE: 'Binary tree (empty / node l v r)',
};

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
    /** Backend graders to dispatch to, in preference order. The first that supports the goal mode grades; the rest add
     * redundancy or mode coverage. Defaults to [PATH_CHECKER]. */
    public graderTypes?: GraderType[] = [DEFAULT_GRADER_TYPE];
    /** Optional slow formal certifier that upgrades the primary grader's fast preliminary verdict (Phase 2b). */
    public certifyingGraderType?: GraderType;
    public partialCreditEnabled?: boolean;
    /** When true the grader treats {@code +} and {@code ·} as commutative/associative for equality comparisons. */
    public acNormalization?: boolean;
    public onlyShowApplicableRules?: boolean;
    public allowVerification?: boolean;
    public manualDerivation?: boolean;
    /** The instructor-supplied worked derivation for this problem (an ordered list of steps). */
    public exampleDerivations?: DerivationStep[];
    /** The variable inducted over in INDUCTION mode. */
    public inductionVariable?: string;
    /** The datatype the induction variable ranges over in INDUCTION mode. Defaults to {@code NAT} (ℕ). */
    public inductionDatatype?: InductionDatatype = 'NAT';

    constructor() {
        this.points = 1;
        this.goalMode = DEFAULT_GOAL_MODE;
        this.graderTypes = [DEFAULT_GRADER_TYPE];
        this.allowVerification = true;
        this.exampleDerivations = [];
    }
}
