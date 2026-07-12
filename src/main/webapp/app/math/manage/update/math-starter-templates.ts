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
const frac = (numerator: MathNode, denominator: MathNode): MathNode => ({ type: 'frac', slots: { numerator: [numerator], denominator: [denominator] } });

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
            problem.graderType = 'REWRITE_CHAIN';
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
            problem.graderType = 'REWRITE_CHAIN';
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
            problem.graderType = 'REWRITE_CHAIN';
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
            problem.graderType = 'REWRITE_CHAIN';
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
            problem.graderType = 'REWRITE_CHAIN';
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
            // Induction is certified by a formal backend; REWRITE_CHAIN cannot grade it.
            problem.graderType = 'CVC5REGATE';
            problem.inductionVariable = 'n';
            problem.goalExpression = eq(add(vr('n'), num('0')), vr('n'));
        },
    },
];
