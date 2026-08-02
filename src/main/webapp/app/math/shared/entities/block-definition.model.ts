import { MathNode } from './math-node.model';
import { RuleConstraint } from './rule-constraint.model';
import { RuleDirection } from './rule-direction.model';

export type Associativity = 'LEFT' | 'NONE';
export type LayoutCategory = 'TERMINAL_NUMBER' | 'TERMINAL_VARIABLE' | 'BINARY_INFIX' | 'FRACTION' | 'UNARY_PREFIX' | 'POWER' | 'SUCCESSOR' | 'FUNCTION_APP';

export interface RewriteRuleModel {
    id: string;
    name: string;
    paletteLatex: string;
    pattern: MathNode;
    template: MathNode;
    /** Whether the rule may be applied in reverse. */
    direction: RuleDirection;
    /** Side conditions checked after a successful match (e.g. {@code c != 0}). Empty / absent for unconditional rules. */
    constraints?: RuleConstraint[];
}

export interface BlockDefinitionModel {
    type: string;
    category: string;
    label: string;
    paletteLatex: string;
    slots?: string[];
    rules?: RewriteRuleModel[];
    /** Code-contributed recursive definitions (e.g. pow_zero / pow_succ), offered in the induction workspace palette. */
    definitions?: RewriteRuleModel[];
    precedence?: number;
    associativity?: Associativity;
    layoutCategory?: LayoutCategory;
    displaySymbol?: string;
    latexSymbol?: string;
    /**
     * When set, this block emits the protocol's generic `apply` node carrying this function name
     * (arguments in the ordered `args` slot) instead of a node of its own `type`, while still rendering
     * with its own `layoutCategory`/`latexSymbol`. Lets a new operator be added as registry data that the
     * grading backends already understand, instead of as a new node type none of them know.
     * `slots.length` is the arity. See `BlockDefinition#getFunctionName()` on the server.
     */
    functionName?: string;
}
