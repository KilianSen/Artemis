import { admin, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseAssessment.id } as any;

/**
 * End-to-end coverage of the tutor manual-assessment workflow: a submission whose automatic grading was escalated to
 * review is picked up from the assessment dashboard (which locks it), scored, and submitted.
 *
 * The problem is routed to the EGGREGATE backend, which is not running in the fast E2E stack, so the submit escalates
 * to manual review (no automatic result) — exactly the state a tutor assessment starts from.
 */
const remoteExerciseTemplate = {
    type: 'math',
    title: 'Math Assessment',
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
            graderTypes: ['EGGREGATE'],
            sourceExpression: { type: 'add', slots: { left: [{ type: 'number', value: '0' }], right: [{ type: 'variable', value: 'x' }] } },
            targetExpression: { type: 'variable', value: 'x' },
        },
    ],
};

test.describe('Math exercise assessment', { tag: '@slow' }, () => {
    let exercise: any;

    test.beforeEach('Create a remote-graded exercise and submit as a student', async ({ login, exerciseAPIRequests, courseOverview, mathParticipation }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createMathExercise({ course }, 'Math Assessment ' + Date.now(), remoteExerciseTemplate);

        // A student submits; automatic grading (EGGREGATE) is unavailable, so it escalates to manual review.
        await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        await courseOverview.startExercise(exercise.id);
        await mathParticipation.shouldShowWorkspace(exercise.id);
        await mathParticipation.applyRuleAtRoot(exercise.id, 'add_zero_left');
        await mathParticipation.submit(exercise.id);
    });

    test('Tutor picks up the escalated submission from the dashboard and grades it', async ({ login, page, exerciseAssessment }) => {
        test.slow();
        await login(tutor, `/course-management/${course.id}/assessment-dashboard/${exercise.id}`);
        await exerciseAssessment.clickHaveReadInstructionsButton();
        await exerciseAssessment.clickStartNewAssessment();

        // The dashboard navigated to the (now locked) submission's assessment view. Score and submit.
        await expect(page.locator('#manualScore')).toBeVisible({ timeout: 30_000 });
        await page.locator('#manualScore').fill('80');
        const responsePromise = page.waitForResponse('**/api/math/math-submissions/*/manual-result*');
        await page.locator('#submit').click();
        const response = await responsePromise;
        expect(response.status()).toBe(200);
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
