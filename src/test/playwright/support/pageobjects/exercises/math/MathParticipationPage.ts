import { Page, expect } from '@playwright/test';
import { getExercise } from '../../../utils';

/**
 * Encapsulates UI selectors and actions for the student-facing math editor (participation).
 *
 * The editor renders inside the exercise split panel once a participation exists. A derivation step is
 * produced by selecting a rule from the palette and then clicking a node on the interactive expression
 * canvas — the selected rule is applied at that node's path.
 */
export class MathParticipationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    private canvas(exerciseId: number) {
        return getExercise(this.page, exerciseId).locator('jhi-math-expression-canvas');
    }

    /** Waits until the derivation workspace (rule palette + interactive canvas) has rendered. */
    async shouldShowWorkspace(exerciseId: number): Promise<void> {
        await expect(this.canvas(exerciseId).locator('.math-node').first()).toBeVisible({ timeout: 30_000 });
    }

    /**
     * Applies the given rule at the root of the current expression: selects the rule chip, then clicks the
     * root operator on the canvas (the click bubbles to the enclosing root node, applying the rule at path []).
     */
    async applyRuleAtRoot(exerciseId: number, ruleId: string): Promise<void> {
        const exercise = getExercise(this.page, exerciseId);
        await exercise.getByTestId(`math-rule-${ruleId}`).first().click();
        await this.canvas(exerciseId).locator('.math-op').first().click();
    }

    /** Asserts the "Math Complete" banner is shown (the derivation reached the goal). */
    async shouldShowComplete(exerciseId: number): Promise<void> {
        await expect(getExercise(this.page, exerciseId).locator('.complete-banner')).toBeVisible();
    }

    /** Submits the participation and returns the math-submissions response. */
    async submit(exerciseId: number) {
        const responsePromise = this.page.waitForResponse('**/api/math/exercises/*/math-submissions');
        await getExercise(this.page, exerciseId).locator('jhi-exercise-submit-button button').click();
        return await responsePromise;
    }

    /** Asserts the graded score badge shows the expected percentage after submission. */
    async shouldShowScore(exerciseId: number, score: number): Promise<void> {
        await expect(getExercise(this.page, exerciseId).getByText(`${score}%`)).toBeVisible({ timeout: 30_000 });
    }
}
