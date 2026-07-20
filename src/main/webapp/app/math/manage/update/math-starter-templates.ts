import { MathNode } from '../../shared/entities/math-node.model';
import { MathProblem } from '../../shared/entities/math-problem.model';

/**
 * Curated starter problems covering the basic equational-reasoning repertoire. An instructor can pick one to
 * pre-fill a {@link MathProblem}'s configuration (goal mode, expressions, grader) instead of starting from a blank
 * problem. The picker in {@code MathProblemEditComponent} applies {@link MathStarterTemplate.apply} in place.
 */

// ── Expression-tree builders (mirror the block registry node types) ─────────────
const num = (value: string): MathNode => ({ type: 'number', value });
const vr = (value: string): MathNode => ({ type: 'variable', value });
const add = (left: MathNode, right: MathNode): MathNode => ({ type: 'add', slots: { left: [left], right: [right] } });
const eq = (left: MathNode, right: MathNode): MathNode => ({ type: 'eq', slots: { left: [left], right: [right] } });
const mul = (left: MathNode, right: MathNode): MathNode => ({ type: 'mul', slots: { left: [left], right: [right] } });
const frac = (numerator: MathNode, denominator: MathNode): MathNode => ({ type: 'frac', slots: { numerator: [numerator], denominator: [denominator] } });
const apply = (name: string, ...args: MathNode[]): MathNode => ({ type: 'apply', value: name, slots: { args } });

export interface MathStarterTemplate {
    /** Stable id (used as the select option value). */
    id: string;
    /** i18n key for the human-readable name. */
    nameKey: string;
    /** i18n key for a one-line description. */
    descriptionKey: string;
    /** Applies the template to a problem in place (the picker emits the problem afterwards). */
    apply: (problem: MathProblem) => void;
}

/** Clears the goal-specific fields so a template starts from a clean slate regardless of the problem's previous mode. */
function resetGoalFields(problem: MathProblem): void {
    problem.sourceExpression = undefined;
    problem.targetExpression = undefined;
    problem.goalExpression = undefined;
    problem.inductionVariable = undefined;
    problem.inductionDatatype = 'NAT';
    problem.certifyingGraderType = undefined;
    problem.exampleDerivations = [];
}

export const MATH_STARTER_TEMPLATES: MathStarterTemplate[] = [
    {
        id: 'left-identity',
        nameKey: 'artemisApp.mathExercise.starterTemplates.leftIdentity.name',
        descriptionKey: 'artemisApp.mathExercise.starterTemplates.leftIdentity.description',
        apply: (problem) => {
            resetGoalFields(problem);
            problem.title = 'Left identity of addition';
            problem.goalMode = 'TRANSFORMATION';
            problem.graderTypes = ['PATH_CHECKER'];
            problem.sourceExpression = add(num('0'), vr('x'));
            problem.targetExpression = vr('x');
        },
    },
    {
        id: 'right-identity',
        nameKey: 'artemisApp.mathExercise.starterTemplates.rightIdentity.name',
        descriptionKey: 'artemisApp.mathExercise.starterTemplates.rightIdentity.description',
        apply: (problem) => {
            resetGoalFields(problem);
            problem.title = 'Right identity of addition';
            problem.goalMode = 'TRANSFORMATION';
            problem.graderTypes = ['PATH_CHECKER'];
            problem.sourceExpression = add(vr('x'), num('0'));
            problem.targetExpression = vr('x');
        },
    },
    {
        id: 'commutativity-addition',
        nameKey: 'artemisApp.mathExercise.starterTemplates.commutativityAddition.name',
        descriptionKey: 'artemisApp.mathExercise.starterTemplates.commutativityAddition.description',
        apply: (problem) => {
            resetGoalFields(problem);
            problem.title = 'Commutativity of addition';
            problem.goalMode = 'EQUATION';
            problem.graderTypes = ['PATH_CHECKER'];
            problem.goalExpression = eq(add(vr('a'), vr('b')), add(vr('b'), vr('a')));
        },
    },
    {
        id: 'associativity-addition',
        nameKey: 'artemisApp.mathExercise.starterTemplates.associativityAddition.name',
        descriptionKey: 'artemisApp.mathExercise.starterTemplates.associativityAddition.description',
        apply: (problem) => {
            resetGoalFields(problem);
            problem.title = 'Associativity of addition';
            problem.goalMode = 'EQUATION';
            problem.graderTypes = ['PATH_CHECKER'];
            problem.goalExpression = eq(add(add(vr('a'), vr('b')), vr('c')), add(vr('a'), add(vr('b'), vr('c'))));
        },
    },
    {
        id: 'fraction-identity',
        nameKey: 'artemisApp.mathExercise.starterTemplates.fractionIdentity.name',
        descriptionKey: 'artemisApp.mathExercise.starterTemplates.fractionIdentity.description',
        apply: (problem) => {
            resetGoalFields(problem);
            problem.title = 'Fraction identity denominator';
            problem.goalMode = 'TRANSFORMATION';
            problem.graderTypes = ['PATH_CHECKER'];
            problem.sourceExpression = frac(vr('a'), num('1'));
            problem.targetExpression = vr('a');
        },
    },
    {
        id: 'induction-add-zero',
        nameKey: 'artemisApp.mathExercise.starterTemplates.inductionAddZero.name',
        descriptionKey: 'artemisApp.mathExercise.starterTemplates.inductionAddZero.description',
        apply: (problem) => {
            resetGoalFields(problem);
            problem.title = 'Induction: n + 0 = n';
            problem.goalMode = 'INDUCTION';
            // Induction is certified by a formal backend; PATH_CHECKER cannot grade it.
            problem.graderTypes = ['CVC5REGATE'];
            problem.inductionVariable = 'n';
            problem.goalExpression = eq(add(vr('n'), num('0')), vr('n'));
        },
    },
    {
        id: 'induction-fact-accumulator',
        nameKey: 'artemisApp.mathExercise.starterTemplates.inductionFactAccumulator.name',
        descriptionKey: 'artemisApp.mathExercise.starterTemplates.inductionFactAccumulator.description',
        apply: (problem) => {
            resetGoalFields(problem);
            problem.title = 'Induction: fact_aux x n = x · fact n';
            problem.goalMode = 'INDUCTION';
            // Induction is certified by a formal backend; PATH_CHECKER cannot grade it.
            problem.graderTypes = ['CVC5REGATE'];
            problem.inductionVariable = 'n';
            // The accumulator-generalised form: proving fact_iter n = fact n directly does not go through — the IH must
            // be usable at a shifted accumulator. The fact/fact_aux definitions ship from ApplyBlockDefinition.
            problem.goalExpression = eq(apply('fact_aux', vr('x'), vr('n')), mul(vr('x'), apply('fact', vr('n'))));
        },
    },
    {
        id: 'induction-list-sum',
        nameKey: 'artemisApp.mathExercise.starterTemplates.inductionListSum.name',
        descriptionKey: 'artemisApp.mathExercise.starterTemplates.inductionListSum.description',
        apply: (problem) => {
            resetGoalFields(problem);
            problem.title = 'List induction: sum l a = a + summa l';
            problem.goalMode = 'INDUCTION';
            // Induction is certified by a formal backend; PATH_CHECKER cannot grade it.
            problem.graderTypes = ['CVC5REGATE'];
            problem.inductionVariable = 'l';
            // Structural induction over a list (nil/cons), not ℕ.
            problem.inductionDatatype = 'LIST';
            // Accumulator-generalised sum correctness; sum/summa definitions ship from ApplyBlockDefinition.
            problem.goalExpression = eq(apply('sum', vr('l'), vr('a')), add(vr('a'), apply('summa', vr('l'))));
        },
    },
    {
        id: 'induction-tree-count',
        nameKey: 'artemisApp.mathExercise.starterTemplates.inductionTreeCount.name',
        descriptionKey: 'artemisApp.mathExercise.starterTemplates.inductionTreeCount.description',
        apply: (problem) => {
            resetGoalFields(problem);
            problem.title = 'Tree induction: aux t a = a + nodes t';
            problem.goalMode = 'INDUCTION';
            // Induction is certified by a formal backend; PATH_CHECKER cannot grade it.
            problem.graderTypes = ['CVC5REGATE'];
            problem.inductionVariable = 't';
            // Structural induction over a binary tree (empty/node) — node has two recursive fields, so two hypotheses.
            problem.inductionDatatype = 'TREE';
            // Accumulator-generalised node-count correctness; nodes/aux definitions ship from ApplyBlockDefinition.
            problem.goalExpression = eq(apply('aux', vr('t'), vr('a')), add(vr('a'), apply('nodes', vr('t'))));
        },
    },
];
