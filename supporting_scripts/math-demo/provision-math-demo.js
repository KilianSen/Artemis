/**
 * Provision a math-exercise demo course: ~50 exercises spanning every goal mode, grader and
 * option combination, plus student submissions in each grading outcome (correct / partial /
 * wrong / awaiting review) so a reviewer sees the whole feature without clicking anything.
 *
 *   node provision-math-demo.js [--course <id>] [--no-submissions]
 *
 * Requires a running Artemis with the e2e seed data (default course 9018, where the
 * artemis_test_user_* logins are already enrolled) and, for the Regate-graded exercises,
 * the grading backends from docker/regate.yml. Exercises whose backend is not running are
 * still created — they simply route to manual review, which is itself one of the states
 * this script is meant to demonstrate.
 *
 * Idempotency: every run creates a fresh batch (exercise short names are randomised), so
 * running it twice gives two batches rather than duplicates-in-place.
 */
const path = require('path');

const PW = process.env.PLAYWRIGHT_PATH || path.join(__dirname, '..', '..', 'src', 'test', 'playwright', 'node_modules', '@playwright', 'test');
const { request } = require(PW);

const BASE = process.env.BASE_URL || 'http://localhost:9000';
const args = process.argv.slice(2);
const COURSE = Number(argFlag('--course') || process.env.COURSE_ID || 9018);
const WITH_SUBMISSIONS = !args.includes('--no-submissions');
const ADMIN = process.env.ARTEMIS_ADMIN || 'artemis_admin';
const STUDENTS = ['artemis_test_user_1', 'artemis_test_user_2', 'artemis_test_user_3'];

function argFlag(name) {
    const i = args.indexOf(name);
    return i >= 0 ? args[i + 1] : undefined;
}

// ── MathNode builders (mirror math-starter-templates.ts) ─────────────────────
const num = (value) => ({ type: 'number', value: String(value) });
const vr = (value) => ({ type: 'variable', value });
const bin = (type, left, right) => ({ type, slots: { left: [left], right: [right] } });
const add = (l, r) => bin('add', l, r);
const sub = (l, r) => bin('sub', l, r);
const mul = (l, r) => bin('mul', l, r);
const eq = (l, r) => bin('eq', l, r);
const frac = (n, d) => ({ type: 'frac', slots: { numerator: [n], denominator: [d] } });
const neg = (inner) => ({ type: 'neg', slots: { inner: [inner] } });
const pow = (base, exponent) => ({ type: 'pow', slots: { base: [base], exponent: [exponent] } });
const succ = (inner) => ({ type: 'succ', slots: { inner: [inner] } });
const apply = (name, ...a) => ({ type: 'apply', value: name, slots: { args: a } });

// Artemis rejects titles outside ^[\p{L}\p{M}\p{N}_\-\s]*$ (Constants.TITLE_NAME_PATTERN):
// no parentheses, colons, plus signs or equals signs in an EXERCISE title. Problem titles are
// not subject to it, so the mathematical notation lives there.
const shortName = () => 'math' + Math.random().toString(36).slice(2, 10);

function problem(title, spec) {
    return {
        title,
        points: spec.points ?? 10,
        goalMode: spec.goalMode ?? 'TRANSFORMATION',
        graderTypes: spec.graderTypes ?? ['PATH_CHECKER'],
        sourceExpression: spec.source,
        targetExpression: spec.target,
        goalExpression: spec.goal,
        inductionVariable: spec.inductionVariable,
        inductionDatatype: spec.inductionDatatype,
        certifyingGraderType: spec.certifier,
        partialCreditEnabled: spec.partialCredit ?? false,
        acNormalization: spec.ac ?? false,
        manualDerivation: spec.manual ?? false,
        onlyShowApplicableRules: spec.onlyApplicable ?? false,
        allowVerification: spec.allowVerification ?? true,
        exampleDerivations: spec.exampleDerivations ?? [],
    };
}

function exercise(title, problems, extra = {}) {
    return {
        type: 'math',
        title,
        shortName: shortName(),
        courseId: COURSE,
        maxPoints: problems.reduce((s, p) => s + p.points, 0),
        bonusPoints: 0,
        includedInOverallScore: 'INCLUDED_COMPLETELY',
        problemStatement: extra.problemStatement ?? 'Rewrite each expression step by step until it matches the goal.',
        presentationScoreEnabled: false,
        secondCorrectionEnabled: false,
        allowFeedbackRequests: false,
        allowComplaintsForAutomaticAssessments: true,
        manualDerivation: false,
        problems,
        ...extra,
    };
}

// ── The catalogue ────────────────────────────────────────────────────────────
// Each entry carries the derivation that SOLVES it, so the script can submit a correct,
// a partial and a wrong answer without hand-writing three payloads per exercise.
// step = { rule, path, result } — `path` indexes flatChildren (slots sorted alphabetically).
const step = (rule, path, result) => ({ rule, path, result });

function catalogue() {
    const x = vr('x'), y = vr('y'), a = vr('a'), b = vr('b'), c = vr('c'), n = vr('n');
    const items = [];
    const push = (title, spec, solution) => items.push({ title, spec, solution });

    // 1. Identity laws — the simplest one-step transformations.
    push('Left identity of addition', { source: add(num(0), x), target: x },
        [step('add_zero_left', [], x)]);
    push('Right identity of addition', { source: add(x, num(0)), target: x },
        [step('add_zero_right', [], x)]);
    push('Left identity of multiplication', { source: mul(num(1), x), target: x },
        [step('mul_one_left', [], x)]);
    push('Right identity of multiplication', { source: mul(x, num(1)), target: x },
        [step('mul_one_right', [], x)]);
    push('Absorbing zero on the left', { source: mul(num(0), x), target: num(0) },
        [step('mul_zero_left', [], num(0))]);
    push('Absorbing zero on the right', { source: mul(x, num(0)), target: num(0) },
        [step('mul_zero_right', [], num(0))]);
    push('Subtracting zero', { source: sub(x, num(0)), target: x },
        [step('sub_zero_right', [], x)]);
    push('Subtracting a value from itself', { source: sub(x, x), target: num(0) },
        [step('sub_self', [], num(0))]);
    push('Double negation', { source: neg(neg(x)), target: x },
        [step('neg_neg', [], x)]);
    push('Negating zero', { source: neg(num(0)), target: num(0) },
        [step('neg_zero', [], num(0))]);

    // 2. Nested identities — the rewrite happens below the root, exercising path encoding.
    push('Identity inside a sum', { source: add(add(num(0), x), y), target: add(x, y) },
        [step('add_zero_left', [0], add(x, y))]);
    push('Identity inside a product', { source: mul(mul(num(1), x), y), target: mul(x, y) },
        [step('mul_one_left', [0], mul(x, y))]);
    push('Identity in the right operand', { source: add(y, mul(x, num(1))), target: add(y, x) },
        [step('mul_one_right', [1], add(y, x))]);
    push('Zero factor inside a sum', { source: add(mul(num(0), x), y), target: add(num(0), y) },
        [step('mul_zero_left', [0], add(num(0), y))]);

    // 3. Two-step chains — where partial credit becomes meaningful.
    push('Collapsing two additive identities', { source: add(num(0), add(x, num(0))), target: x, partialCredit: true },
        [step('add_zero_left', [], add(x, num(0))), step('add_zero_right', [], x)]);
    push('Collapsing two multiplicative identities', { source: mul(num(1), mul(x, num(1))), target: x, partialCredit: true },
        [step('mul_one_left', [], mul(x, num(1))), step('mul_one_right', [], x)]);
    push('From a zero product to a clean sum', { source: add(mul(num(0), x), y), target: y, partialCredit: true },
        [step('mul_zero_left', [0], add(num(0), y)), step('add_zero_left', [], y)]);
    push('Unfolding a double negation inside a sum', { source: add(neg(neg(x)), num(0)), target: x, partialCredit: true },
        [step('neg_neg', [0], add(x, num(0))), step('add_zero_right', [], x)]);

    // 4. Fractions — including the guarded cancellation rule.
    push('Denominator of one', { source: frac(x, num(1)), target: x },
        [step('frac_one_denom', [], x)]);
    push('Cancelling a common factor', { source: frac(mul(c, a), mul(c, b)), target: frac(a, b) },
        [step('frac_mul_cancel_left', [], frac(a, b))]);
    push('Cancelling then simplifying', { source: frac(mul(c, a), mul(c, num(1))), target: a, partialCredit: true },
        [step('frac_mul_cancel_left', [], frac(a, num(1))), step('frac_one_denom', [], a)]);

    // 5. Commutativity and associativity — the AC-sensitive family. With AC normalisation on,
    //    the target matches up to reordering; with it off the student must cite the rule.
    push('Commutativity of addition', { source: add(a, b), target: add(b, a) },
        [step('add_comm', [], add(b, a))]);
    push('Commutativity of multiplication', { source: mul(a, b), target: mul(b, a) },
        [step('mul_comm', [], mul(b, a))]);
    push('Associativity of addition', { source: add(add(a, b), c), target: add(a, add(b, c)) },
        [step('add_assoc', [], add(a, add(b, c)))]);
    push('Associativity of multiplication', { source: mul(mul(a, b), c), target: mul(a, mul(b, c)) },
        [step('mul_assoc', [], mul(a, mul(b, c)))]);
    push('Reordering under AC normalisation', { source: add(a, b), target: add(b, a), ac: true },
        [step('add_comm', [], add(b, a))]);
    push('Reordering a product under AC normalisation', { source: mul(a, b), target: mul(b, a), ac: true },
        [step('mul_comm', [], mul(b, a))]);

    // 6. Distributivity — two directions of the same law.
    push('Expanding a product over a sum', { source: mul(a, add(b, c)), target: add(mul(a, b), mul(a, c)) },
        [step('mul_distrib', [], add(mul(a, b), mul(a, c)))]);
    push('Expanding from the right', { source: mul(add(b, c), a), target: add(mul(b, a), mul(c, a)) },
        [step('mul_distrib_right', [], add(mul(b, a), mul(c, a)))]);
    push('Expanding then absorbing a zero', { source: mul(num(0), add(b, c)), target: num(0), partialCredit: true },
        [step('mul_zero_left', [], num(0))]);

    // 7. Additive inverse and subtraction rewriting.
    push('Additive inverse', { source: add(x, neg(x)), target: num(0) },
        [step('add_inverse', [], num(0))]);
    push('Subtraction as addition of a negative', { source: sub(a, b), target: add(a, neg(b)) },
        [step('sub_as_add_neg', [], add(a, neg(b)))]);

    // 8. Equation mode — reduce an equation to a tautology rather than hit a target tree.
    push('Equation commutativity of addition', { goalMode: 'EQUATION', goal: eq(add(a, b), add(b, a)), ac: true }, []);
    push('Equation commutativity of multiplication', { goalMode: 'EQUATION', goal: eq(mul(a, b), mul(b, a)), ac: true }, []);
    push('Equation associativity of addition', { goalMode: 'EQUATION', goal: eq(add(add(a, b), c), add(a, add(b, c))), ac: true }, []);
    push('Equation with an explicit rewrite', { goalMode: 'EQUATION', goal: eq(add(num(0), x), x) },
        [step('add_zero_left', [0], eq(x, x))]);

    // 9. Induction — the Regate certifiers. cvc5 handles ℕ, lists and trees; coq handles ℕ.
    const indDefs = { goalMode: 'INDUCTION', inductionVariable: 'n', inductionDatatype: 'NAT' };
    push('Induction on the additive identity', { ...indDefs, goal: eq(add(n, num(0)), n), graderTypes: ['CVC5REGATE'] }, []);
    push('Induction one to the n', { ...indDefs, goal: eq(pow(num(1), n), num(1)), graderTypes: ['CVC5REGATE'] }, []);
    push('Induction on a power product', { ...indDefs, goal: eq(mul(pow(a, n), pow(b, n)), pow(mul(a, b), n)), graderTypes: ['COQREGATE'] }, []);
    push('Induction on a sum of exponents', { ...indDefs, goal: eq(pow(a, add(vr('m'), n)), mul(pow(a, vr('m')), pow(a, n))), graderTypes: ['COQREGATE'] }, []);
    push('Induction with a certifier', { ...indDefs, goal: eq(add(n, num(0)), n), graderTypes: ['CVC5REGATE'], certifier: 'COQREGATE' }, []);

    // 10. Grader variety on ordinary transformations — eggregate is the e-graph backend.
    push('Identity graded by the e-graph', { source: add(num(0), x), target: x, graderTypes: ['EGGREGATE'] },
        [step('add_zero_left', [], x)]);
    push('Cancellation graded by the e-graph', { source: frac(mul(c, a), mul(c, b)), target: frac(a, b), graderTypes: ['EGGREGATE'] },
        [step('frac_mul_cancel_left', [], frac(a, b))]);
    push('Path checker as fallback behind the e-graph', { source: mul(num(1), x), target: x, graderTypes: ['EGGREGATE', 'PATH_CHECKER'] },
        [step('mul_one_left', [], x)]);

    // 11. Editor options — manual derivation, restricted palette, verification off.
    push('Manual derivation practice', { source: add(num(0), x), target: x, manual: true },
        [step('add_zero_left', [], x)]);
    push('Manual derivation with a longer chain', { source: mul(num(1), add(num(0), x)), target: x, manual: true, partialCredit: true },
        [step('mul_one_left', [], add(num(0), x)), step('add_zero_left', [], x)]);
    push('Only applicable rules shown', { source: add(x, num(0)), target: x, onlyApplicable: true },
        [step('add_zero_right', [], x)]);
    push('Verification button disabled', { source: mul(x, num(1)), target: x, allowVerification: false },
        [step('mul_one_right', [], x)]);

    // 12. Awaiting review — leanregate is not part of the default docker/regate.yml set, so a
    //     submission here cannot be graded automatically and routes to a tutor. That is the
    //     honest-inconclusive path, and it is worth seeing in the demo course.
    push('Formal proof routed to review', { source: add(num(0), x), target: x, graderTypes: ['LEANREGATE'] },
        [step('add_zero_left', [], x)]);

    return items;
}

// ── HTTP helpers ─────────────────────────────────────────────────────────────
async function login(ctx, username) {
    const res = await ctx.post(`${BASE}/api/core/public/authenticate`, { data: { username, password: username, rememberMe: true } });
    if (!res.ok()) throw new Error(`login ${username} -> ${res.status()}`);
}

async function createExercise(ctx, payload) {
    const res = await ctx.post(`${BASE}/api/math/math-exercises`, { data: payload });
    if (!res.ok()) throw new Error(`create "${payload.title}" -> ${res.status()} ${(await res.text()).slice(0, 160)}`);
    return res.json();
}

/**
 * Start the exercise for the logged-in student. A submission without a StudentParticipation is
 * rejected with 424 "No participation found" — the UI does this behind its "Start exercise"
 * button, so provisioning has to do it explicitly. Already-started is not an error.
 */
async function startParticipation(ctx, exerciseId) {
    const res = await ctx.post(`${BASE}/api/exercise/exercises/${exerciseId}/participations`);
    if (!res.ok() && res.status() !== 400 && res.status() !== 409) {
        throw new Error(`start ex=${exerciseId} -> ${res.status()} ${(await res.text()).slice(0, 120)}`);
    }
}

/** Submit `steps` for the exercise's first problem. `steps` may be empty (an untouched submission). */
async function submit(ctx, exerciseId, problemId, steps) {
    const res = await ctx.post(`${BASE}/api/math/exercises/${exerciseId}/math-submissions`, {
        data: {
            submitted: true,
            answers: [{
                problemId,
                steps: steps.map((s, i) => ({
                    stepIndex: i,
                    appliedRuleId: s.rule,
                    targetNodePath: s.path,
                    resultExpression: s.result,
                    direction: 'FORWARD',
                })),
            }],
        },
    });
    if (!res.ok()) throw new Error(`submit ex=${exerciseId} -> ${res.status()} ${(await res.text()).slice(0, 160)}`);
    return res.json();
}

// ── main ─────────────────────────────────────────────────────────────────────
(async () => {
    const ctx = await request.newContext({ ignoreHTTPSErrors: true });
    await login(ctx, ADMIN);

    const items = catalogue();
    console.log(`Provisioning ${items.length} math exercises into course ${COURSE} …`);

    const created = [];
    for (const item of items) {
        const p = problem(item.title, item.spec);
        try {
            const ex = await createExercise(ctx, exercise(item.title, [p]));
            created.push({ ...item, exercise: ex });
            process.stdout.write('.');
        } catch (e) {
            console.log(`\n  ✗ ${item.title}: ${String(e.message).slice(0, 140)}`);
        }
    }
    console.log(`\n${created.length}/${items.length} exercises created.`);

    if (!WITH_SUBMISSIONS) {
        console.log('--no-submissions: skipping the student answers.');
        await ctx.dispose();
        return;
    }

    // Spread outcomes across the catalogue so the tutor dashboard shows a realistic mix:
    // solved, half-solved, wrong, and untouched-but-submitted.
    const outcomes = { correct: 0, partial: 0, wrong: 0, empty: 0, skipped: 0, failed: 0 };
    let i = 0;
    for (const c of created) {
        const problemId = c.exercise.problems?.[0]?.id;
        const solution = c.solution ?? [];
        if (!problemId) { outcomes.skipped++; continue; }

        // Which answer this student gives. A PARTIAL score is only possible where the problem
        // enables partial credit AND the solution has more than one step — otherwise dropping a
        // step just lands on 0. Assigning "partial" by a blind cycle produced exactly one real
        // partial out of eight attempts, so pick by capability first and cycle over the rest.
        const canPartial = c.spec.partialCredit === true && solution.length > 1;
        const kind = solution.length === 0 ? 'empty'
            : canPartial ? 'partial'
                : ['correct', 'correct', 'wrong', 'empty'][i % 4];
        let steps;
        if (kind === 'correct') steps = solution;
        else if (kind === 'partial') steps = solution.slice(0, solution.length - 1);
        else if (kind === 'wrong') steps = [{ ...solution[0], result: num(42) }];   // a result the rule cannot yield
        else steps = [];

        try {
            await login(ctx, STUDENTS[i % STUDENTS.length]);
            await startParticipation(ctx, c.exercise.id);
            await submit(ctx, c.exercise.id, problemId, steps);
            outcomes[kind]++;
        } catch (e) {
            outcomes.failed++;
            console.log(`  ✗ submit "${c.title}": ${String(e.message).slice(0, 120)}`);
        }
        i++;
    }

    console.log('\nSubmissions:', Object.entries(outcomes).map(([k, v]) => `${k}=${v}`).join(' '));
    console.log(`\nDone. Open ${BASE}/courses/${COURSE}/exercises as a student, or`);
    console.log(`${BASE}/course-management/${COURSE}/assessment-dashboard as a tutor.`);
    await ctx.dispose();
})().catch((e) => {
    console.error(e);
    process.exit(1);
});
