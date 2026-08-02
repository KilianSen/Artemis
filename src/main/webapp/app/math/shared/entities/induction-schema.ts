import { MathNode, mathNodeToLatex, substituteVariable, wildcardizeExcept } from 'app/math/shared/entities/math-node.model';
import { InductionDatatype, MathProblem } from 'app/math/shared/entities/math-problem.model';
import { RewriteRuleModel } from 'app/math/shared/entities/block-definition.model';

/** The synthetic rule id prefix under which an induction hypothesis is offered as an applicable rule. */
export const IH_RULE_ID = 'induction_hypothesis';

/** One induction hypothesis {@code P(fieldVar)}: the goal with the induction variable replaced by a recursive field. */
export interface InductionHypothesis {
    fieldVar: string;
    ruleId: string;
    equation: MathNode;
    index: number;
}

/** The base/step constructors and recursive-field variables that define an induction over a datatype. */
export interface InductionSchema {
    /** The base-case term the induction variable is instantiated to (e.g. {@code 0}, {@code nil}). */
    baseTerm: MathNode;
    /** The step-case constructor term (e.g. {@code S n}, {@code cons h t}, {@code node l v r}). */
    stepTerm: MathNode;
    /** The variables at the step constructor's recursive positions — one induction hypothesis per entry. */
    recursiveFieldVars: string[];
}

const vr = (value: string): MathNode => ({ type: 'variable', value });
const app = (name: string, ...args: MathNode[]): MathNode => ({ type: 'apply', value: name, slots: { args } });

/**
 * A compact human-readable rendering of a constructor term for the base/step labels — {@code 0}, {@code S n},
 * {@code nil}, {@code cons h t}, {@code node l v r}. Deliberately plain text (not LaTeX): these appear inside
 * {@code P(…)} in the section headings and info message.
 */
export function termToPlain(node: MathNode): string {
    switch (node.type) {
        case 'succ':
            return `S ${(node.slots?.['inner'] ?? []).map(termToPlain).join(' ')}`;
        case 'apply': {
            const args = (node.slots?.['args'] ?? []).map(termToPlain);
            return args.length ? `${node.value} ${args.join(' ')}` : `${node.value}`;
        }
        default:
            return node.value ?? '';
    }
}

/**
 * The induction schema for a datatype. ℕ keeps the legacy {@code 0}/{@code S n} forms with a single IH; lists and
 * binary trees use {@code apply}-encoded constructors ({@code nil}/{@code cons}, {@code empty}/{@code node}) with
 * one IH per recursive field. Mirrors the server {@code exercise.datatype} descriptor built in {@code RegateRequestMapper}.
 * <p>
 * Shared by the participation editor (which builds the two workspaces) and the assessment view (which replays the
 * submitted steps), so both agree on what {@code P(base)} and {@code P(step)} are.
 */
export function schemaFor(datatype: InductionDatatype, inductionVar: string): InductionSchema {
    switch (datatype) {
        case 'LIST':
            return { baseTerm: app('nil'), stepTerm: app('cons', vr('h'), vr('t')), recursiveFieldVars: ['t'] };
        case 'TREE':
            return { baseTerm: app('empty'), stepTerm: app('node', vr('l'), vr('v'), vr('r')), recursiveFieldVars: ['l', 'r'] };
        default:
            return { baseTerm: { type: 'number', value: '0' }, stepTerm: { type: 'succ', slots: { inner: [vr(inductionVar)] } }, recursiveFieldVars: [inductionVar] };
    }
}

/** The induction variable a problem inducts over, defaulting to {@code n}. */
export function inductionVarOf(problem: MathProblem): string {
    return problem.inductionVariable || 'n';
}

/** The schema (base/step constructors, recursive fields) for a problem's induction datatype. */
export function schemaOf(problem: MathProblem): InductionSchema {
    return schemaFor(problem.inductionDatatype ?? 'NAT', inductionVarOf(problem));
}

/**
 * The problem's goal with the induction variable instantiated to {@code term} — i.e. the obligation
 * {@code P(term)} proved by one of the two cases. Returns undefined when the problem has no goal yet.
 */
export function substitutedGoal(problem: MathProblem, term: MathNode): MathNode | undefined {
    const goal = problem.goalExpression;
    return goal ? substituteVariable(goal, inductionVarOf(problem), term) : undefined;
}

/**
 * The induction hypotheses — one per recursive field of the step constructor: {@code P(n)} for ℕ, {@code P(t)}
 * for a list, {@code P(l)} and {@code P(r)} for a binary tree. Each is the goal with the induction variable
 * replaced by that recursive field; the field variable is what stays literal when the IH is schematised.
 */
export function hypothesesFor(problem: MathProblem): InductionHypothesis[] {
    const goal = problem.goalExpression;
    if (!goal) {
        return [];
    }
    const fields = schemaOf(problem).recursiveFieldVars;
    return fields.map((fieldVar, index) => ({
        fieldVar,
        // A single IH keeps the legacy id `induction_hypothesis`; multiple IHs (a tree) are disambiguated by field.
        ruleId: fields.length > 1 ? `${IH_RULE_ID}_${fieldVar}` : IH_RULE_ID,
        equation: substituteVariable(goal, inductionVarOf(problem), { type: 'variable', value: fieldVar }),
        index,
    }));
}

/**
 * Each induction hypothesis as an applicable (Leibniz) rewrite rule for the step case's palette. The accumulator
 * parameters become wildcards so the hypothesis can be applied at a shifted accumulator, while the recursive field
 * variable stays literal — so the schema matches {@code P(f)} exactly and never the step constructor, preserving
 * soundness. The backend re-checks every applied instance.
 */
export function ihRulesFor(problem: MathProblem): RewriteRuleModel[] {
    return hypothesesFor(problem).flatMap((h) => {
        const lhs = h.equation.slots?.['left']?.[0];
        const rhs = h.equation.slots?.['right']?.[0];
        if (!lhs || !rhs) {
            return [];
        }
        return [
            {
                id: h.ruleId,
                name: 'Induction hypothesis',
                paletteLatex: mathNodeToLatex(h.equation),
                pattern: wildcardizeExcept(lhs, h.fieldVar),
                template: wildcardizeExcept(rhs, h.fieldVar),
                direction: 'BIDIRECTIONAL' as const,
                constraints: [],
            },
        ];
    });
}
