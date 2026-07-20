import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

/**
 * End-to-end coverage of the core equational-reasoning loop as a student experiences it: open the math
 * editor, apply a rewrite rule to transform the start expression into the goal, and submit for grading.
 *
 * The exercise proves {@code 0 + x = x} in a single {@code add_zero_left} step and is graded by the
 * in-process {@code PATH_CHECKER} engine, so grading is synchronous and the result appears on submit
 * (no remote backend or websocket needed — that path is covered by the server-side live test).
 */
const solvableExerciseTemplate = {
    type: 'math',
    title: 'Math EqReasoning',
    maxPoints: 10,
    bonusPoints: 0,
    includedInOverallScore: 'INCLUDED_COMPLETELY',
    problemStatement: 'Prove that 0 + x = x.',
    presentationScoreEnabled: false,
    secondCorrectionEnabled: false,
    allowFeedbackRequests: false,
    allowComplaintsForAutomaticAssessments: false,
    manualDerivation: false,
    problems: [
        {
            title: 'Problem 1',
            points: 10,
            goalMode: 'TRANSFORMATION',
            graderTypes: ['PATH_CHECKER'],
            sourceExpression: { type: 'add', slots: { left: [{ type: 'number', value: '0' }], right: [{ type: 'variable', value: 'x' }] } },
            targetExpression: { type: 'variable', value: 'x' },
        },
    ],
};

test.describe('Math exercise participation', { tag: '@fast' }, () => {
    let exercise: any;

    test.beforeEach('Create a solvable math exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createMathExercise({ course }, 'Math EqReasoning ' + Date.now(), solvableExerciseTemplate);
        expect(exercise.id).toBeTruthy();
    });

    test('Solves a transformation problem via the rule palette and gets graded', async ({ login, courseOverview, mathParticipation }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        await courseOverview.startExercise(exercise.id);
        await courseOverview.shouldShowExerciseTitleInHeader(exercise.title);

        // Apply add_zero_left at the root: 0 + x -> x, which reaches the goal.
        await mathParticipation.shouldShowWorkspace(exercise.id);
        await mathParticipation.applyRuleAtRoot(exercise.id, 'add_zero_left');
        await mathParticipation.shouldShowComplete(exercise.id);

        // Submit — PATH_CHECKER grades synchronously, so the authoritative result comes back on the response.
        const response = await mathParticipation.submit(exercise.id);
        expect(response.status()).toBe(200);
        const submission = await response.json();
        expect(submission.submitted).toBe(true);
        expect(submission.results?.[0]?.score).toBe(100);

        await mathParticipation.shouldShowScore(exercise.id, 100);
    });

    test('Escalates to tutor review when the remote grader is unavailable', async ({ login, exerciseAPIRequests, courseOverview, mathParticipation }) => {
        // A problem routed to a remote (Regate) backend that is not running in the fast E2E stack: grading fails fast and
        // the submission is escalated to manual review, which the student sees as an "awaiting tutor review" state.
        const remoteTemplate = {
            ...solvableExerciseTemplate,
            problems: [{ ...solvableExerciseTemplate.problems[0], graderTypes: ['EGGREGATE'] }],
        };
        await login(admin);
        const remoteExercise = await exerciseAPIRequests.createMathExercise({ course }, 'Math EqReasoning Remote ' + Date.now(), remoteTemplate);

        try {
            await login(studentOne, `/courses/${course.id}/exercises/${remoteExercise.id}`);
            await courseOverview.startExercise(remoteExercise.id);

            await mathParticipation.shouldShowWorkspace(remoteExercise.id);
            await mathParticipation.applyRuleAtRoot(remoteExercise.id, 'add_zero_left');
            await mathParticipation.submit(remoteExercise.id);

            // The async grade fails (no backend) → REVIEW is pushed over the websocket → the review banner appears.
            await mathParticipation.shouldShowUnderReview(remoteExercise.id);
        } finally {
            await login(admin);
            await exerciseAPIRequests.deleteMathExercise(remoteExercise.id);
        }
    });

    test.afterEach('Delete created math exercise', async ({ login, exerciseAPIRequests }) => {
        if (exercise?.id) {
            await login(admin);
            await exerciseAPIRequests.deleteMathExercise(exercise.id);
            exercise = undefined;
        }
    });

    // Seed courses are persistent — no cleanup needed
});
