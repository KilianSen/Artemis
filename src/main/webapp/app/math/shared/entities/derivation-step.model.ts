import { MathNode } from './math-node.model';
import { StepDirection } from './rule-direction.model';

/** Which derivation of an answer a step belongs to: MAIN (transformation/equation) or BASE/STEP (induction). */
export type DerivationRole = 'MAIN' | 'BASE' | 'STEP';

/** How a step is justified: A (rule application) or B (Leibniz substitution, e.g. the induction hypothesis). */
export type StepKind = 'A' | 'B';

export interface DerivationStep {
    id?: number;
    stepIndex: number;
    appliedRuleId: string;
    targetNodePath: number[];
    resultExpression: MathNode;
    /** Direction in which the rule was applied. Defaults to FORWARD when unset. */
    direction?: StepDirection;
    /** Which induction derivation this step belongs to; defaults to MAIN (non-induction). */
    derivationRole?: DerivationRole;
    /** How the step is justified; defaults to A (rule application). */
    kind?: StepKind;
    /** The substituted equality for a kind-B step (e.g. the induction hypothesis P(n)). */
    substitutionEquation?: MathNode;
}
