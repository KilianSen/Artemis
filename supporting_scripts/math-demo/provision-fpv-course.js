/**
 * Provision a course from the FPV (Functional Programming and Verification) MiniOCaml exercise
 * set — restricted to the parts the Artemis math exercise type can actually express and grade.
 *
 *   node provision-fpv-course.js [--course <id>] [--no-submissions]
 *
 * WHAT IS COMPATIBLE, AND WHY
 * The math exercise type grades EQUATIONAL reasoning: rewrite chains over a MathNode term, and
 * structural induction over ℕ / lists / binary trees (InductionDatatype). Recursive function
 * behaviour reaches a backend as trusted `definitions`, which today are code-contributed by
 * ApplyBlockDefinition — fact/fact_aux, summa/sum, nodes/aux. Those are exactly the functions of
 * three FPV exercises, so those three port over; the rest of the set is big-step operational
 * semantics (proof trees), termination arguments, a semantics extension, or multiple-choice, none
 * of which is a term-rewriting problem. See the report at the end of the run.
 *
 * Each ported exercise becomes TWO problems, mirroring how the original proof actually decomposes:
 *   1. the accumulator-generalised lemma, by induction, certified by cvc5regate;
 *   2. the closing equation that gets from the lemma back to the original claim.
 * The lemmas go to cvc5regate, the closing equations to eggregate — its home ground, since each is a
 * single rewrite by one catalogue rule.
 *
 * Their terms mention `fact`/`summa`/`nodes`, so eggregate must be built from protocol-1.1 source, where
 * every backend parses and compiles `apply` (see GRADING_PROTOCOL.md, "apply support matrix"). An older
 * image whose MathNode reader predates `apply` rejects the request outright with HTTP 400 "malformed
 * MathNode in exercise.source: 'apply'", and Artemis routes the problem to tutor review. `docker compose
 * -f docker/regate.yml up -d --build` (ABGABE.md step 3) builds from the checkout, so a reviewer
 * following the guide gets a current image; a long-lived dev container may not be one.
 *
 * cvc5regate also grades these (measured: proven_equal, score 100, certified). Artemis used to declare it
 * induction-only in GraderType.java, which rejected that pairing with 400 "Grader CVC5REGATE cannot grade
 * goal mode EQUATION"; that capability-declaration gap is fixed — CVC5REGATE now declares
 * TRANSFORMATION/EQUATION/INDUCTION, so it is a valid alternative for the closing equations too.
 * The derivations submitted here are the ones pinned by Regate conformance fixtures 28/29/30.
 * Those fixtures run with `ac_normalization`, and so do these problems (`ac: true`): all three
 * inductive steps close on an associativity/commutativity rearrangement — e.g. fact ends at
 * `(x·S n)·fact n = x·(S n·fact n)` — and cvc5_induction requires `is_reflexive(final, ac)`
 * before it will backstop the induction leap. Without the flag the step is rejected as
 * "inductive step did not reduce both sides to a common form".
 */
const path = require('path');

const PW = process.env.PLAYWRIGHT_PATH || path.join(__dirname, '..', '..', 'src', 'test', 'playwright', 'node_modules', '@playwright', 'test');
const { request } = require(PW);

const BASE = process.env.BASE_URL || 'http://localhost:9000';
const args = process.argv.slice(2);
const WITH_SUBMISSIONS = !args.includes('--no-submissions');
const ADMIN = process.env.ARTEMIS_ADMIN || 'artemis_admin';
const STUDENTS = ['artemis_test_user_1', 'artemis_test_user_2', 'artemis_test_user_3'];

function argFlag(name) {
    const i = args.indexOf(name);
    return i >= 0 ? args[i + 1] : undefined;
}
const EXISTING_COURSE = argFlag('--course') ? Number(argFlag('--course')) : undefined;

// ── MathNode builders (mirror math-starter-templates.ts) ─────────────────────
const num = (value) => ({ type: 'number', value: String(value) });
const vr = (value) => ({ type: 'variable', value });
const bin = (type, left, right) => ({ type, slots: { left: [left], right: [right] } });
const add = (l, r) => bin('add', l, r);
const mul = (l, r) => bin('mul', l, r);
const eq = (l, r) => bin('eq', l, r);
const succ = (inner) => ({ type: 'succ', slots: { inner: [inner] } });
const apply = (name, ...a) => ({ type: 'apply', value: name, slots: { args: a } });

// step roles: MAIN for a plain rewrite chain, BASE / STEP for the two obligations of an induction
// proof. All of an answer's derivations live in one ordered list, partitioned by role.
const step = (rule, path_, result, role) => ({ rule, path: path_, result, role: role ?? 'MAIN' });
const base = (rule, path_, result) => step(rule, path_, result, 'BASE');
const ind = (rule, path_, result) => step(rule, path_, result, 'STEP');
// Type-B: rewrite with the induction hypothesis rather than a rule (GRADING_PROTOCOL.md).
const hyp = (path_, equation, result) => ({ rule: null, kind: 'B', path: path_, equation, result, role: 'STEP' });

const n = vr('n'), x = vr('x'), a = vr('a'), l = vr('l'), h = vr('h'), t = vr('t'), r = vr('r'), v = vr('v');
const nil = apply('nil'), empty = apply('empty');
const cons = (hh, tt) => apply('cons', hh, tt);
const node = (ll, vv, rr) => apply('node', ll, vv, rr);

// ── The three portable FPV exercises ────────────────────────────────────────
function catalogue() {
    return [
        {
            title: 'Week 11 Tutorial 03 What The Fact',
            source: 17215,
            statement: `# What The Fact (FPV 17215)

Let these functions be defined:

\`\`\`ocaml
let rec fact n = match n with 0 -> 1
  | n -> n * fact (n - 1)

let rec fact_aux x n = match n with 0 -> x
  | n -> fact_aux (n * x) (n - 1)

let fact_iter = fact_aux 1
\`\`\`

The original exercise asks you to show \`fact_iter n = fact n\` for all \`n\` in N_0.

A direct induction does not close: the induction hypothesis has to be usable at a *shifted*
accumulator. So the proof is split the way it is actually done on paper:

1. **Problem 1** proves the accumulator-generalised lemma \`fact_aux x n = x * fact n\` by
   induction on \`n\`, certified by cvc5.
2. **Problem 2** closes the gap from the lemma to the original claim: instantiating the lemma at
   \`x = 1\` gives \`fact_iter n = 1 * fact n\`, and this problem discharges \`1 * fact n = fact n\`.`,
            problems: [
                {
                    title: 'Lemma fact_aux x n = x times fact n by induction on n',
                    points: 10,
                    spec: { goalMode: 'INDUCTION', inductionVariable: 'n', inductionDatatype: 'NAT', graderTypes: ['CVC5REGATE'], ac: true, goal: eq(apply('fact_aux', x, n), mul(x, apply('fact', n))) },
                    // Regate conformance fixture 28-apply-generalized-ih-fact.
                    solution: [
                        base('fact_aux_zero', [0], eq(x, mul(x, apply('fact', num(0))))),
                        base('fact_zero', [1, 1], eq(x, mul(x, num(1)))),
                        base('mul_one_right', [1], eq(x, x)),
                        ind('fact_aux_succ', [0], eq(apply('fact_aux', mul(x, succ(n)), n), mul(x, apply('fact', succ(n))))),
                        hyp([0], eq(apply('fact_aux', mul(x, succ(n)), n), mul(mul(x, succ(n)), apply('fact', n))), eq(mul(mul(x, succ(n)), apply('fact', n)), mul(x, apply('fact', succ(n))))),
                        ind('fact_succ', [1, 1], eq(mul(mul(x, succ(n)), apply('fact', n)), mul(x, mul(succ(n), apply('fact', n))))),
                    ],
                },
                {
                    title: 'Closing step 1 times fact n = fact n',
                    points: 5,
                    spec: { goalMode: 'EQUATION', graderTypes: ['EGGREGATE'], goal: eq(mul(num(1), apply('fact', n)), apply('fact', n)) },
                    solution: [step('mul_one_left', [0], eq(apply('fact', n), apply('fact', n)))],
                },
            ],
        },
        {
            title: 'Week 12 Tutorial 01 Arithmetic 101',
            source: 17216,
            statement: `# Arithmetic 101 (FPV 17216)

Let these functions be defined:

\`\`\`ocaml
let rec summa l = match l with
  | [] -> 0
  | h :: t -> h + summa t

let rec sum l a = match l with
  | [] -> a
  | h :: t -> sum t (h + a)

let rec mul i j a = match i <= 0 with
  | true -> a
  | false -> mul (i - 1) j (j + a)
\`\`\`

The original exercise asks for \`mul c (sum l 0) 0 = c * summa l\`.

The part that is equational reasoning over a recursive list function is the **sum lemma**, and it
is the load-bearing one: the outer \`mul\` is a third accumulator recursion over the counter \`c\`,
whose definition is not in the block palette, so the full statement is out of scope here.

1. **Problem 1** proves \`sum l a = a + summa l\` by structural induction on the list \`l\`
   (nil/cons), certified by cvc5.
2. **Problem 2** instantiates it at \`a = 0\` — \`sum l 0 = 0 + summa l\` — and discharges
   \`0 + summa l = summa l\`.`,
            problems: [
                {
                    title: 'Lemma sum l a = a + summa l by induction on the list l',
                    points: 10,
                    spec: { goalMode: 'INDUCTION', inductionVariable: 'l', inductionDatatype: 'LIST', graderTypes: ['CVC5REGATE'], ac: true, goal: eq(apply('sum', l, a), add(a, apply('summa', l))) },
                    // Regate conformance fixture 29-datatype-list-induction.
                    solution: [
                        base('sum_nil', [0], eq(a, add(a, apply('summa', nil)))),
                        base('summa_nil', [1, 1], eq(a, add(a, num(0)))),
                        base('add_zero_right', [1], eq(a, a)),
                        ind('sum_cons', [0], eq(apply('sum', t, add(a, h)), add(a, apply('summa', cons(h, t))))),
                        hyp([0], eq(apply('sum', t, add(a, h)), add(add(a, h), apply('summa', t))), eq(add(add(a, h), apply('summa', t)), add(a, apply('summa', cons(h, t))))),
                        ind('summa_cons', [1, 1], eq(add(add(a, h), apply('summa', t)), add(a, add(h, apply('summa', t))))),
                    ],
                },
                {
                    title: 'Closing step 0 + summa l = summa l',
                    points: 5,
                    spec: { goalMode: 'EQUATION', graderTypes: ['EGGREGATE'], goal: eq(add(num(0), apply('summa', l)), apply('summa', l)) },
                    solution: [step('add_zero_left', [0], eq(apply('summa', l), apply('summa', l)))],
                },
            ],
        },
        {
            title: 'Week 12 Supplemental 01 Counting Nodes',
            source: 17217,
            statement: `# Counting Nodes (FPV 17217)

A binary tree and two ways of counting its nodes:

\`\`\`ocaml
type tree = Node of tree * tree | Empty

let rec nodes t = match t with Empty -> 0
    | Node (l,r) -> 1 + (nodes l) + (nodes r)

let rec count t =
  let rec aux t a = match t with Empty -> a
      | Node (l,r) -> aux r (aux l (a+1))
  in
  aux t 0
\`\`\`

The original exercise asks you to prove or disprove \`nodes t = count t\`. It holds, and the proof
is again an accumulator generalisation — note that \`node\` has **two** recursive fields, so the
inductive step gets **two** induction hypotheses, one per subtree.

1. **Problem 1** proves \`aux t a = a + nodes t\` by structural induction on the tree \`t\`,
   certified by cvc5.
2. **Problem 2** instantiates it at \`a = 0\` — \`count t = aux t 0 = 0 + nodes t\` — and discharges
   \`0 + nodes t = nodes t\`.`,
            problems: [
                {
                    title: 'Lemma aux t a = a + nodes t by induction on the tree t',
                    points: 10,
                    spec: { goalMode: 'INDUCTION', inductionVariable: 't', inductionDatatype: 'TREE', graderTypes: ['CVC5REGATE'], ac: true, goal: eq(apply('aux', t, a), add(a, apply('nodes', t))) },
                    // Regate conformance fixture 30-datatype-tree-induction.
                    solution: [
                        base('aux_empty', [0], eq(a, add(a, apply('nodes', empty)))),
                        base('nodes_empty', [1, 1], eq(a, add(a, num(0)))),
                        base('add_zero_right', [1], eq(a, a)),
                        ind('aux_node', [0], eq(apply('aux', r, apply('aux', l, add(a, num(1)))), add(a, apply('nodes', node(l, v, r))))),
                        hyp([0, 1], eq(apply('aux', l, add(a, num(1))), add(add(a, num(1)), apply('nodes', l))),
                            eq(apply('aux', r, add(add(a, num(1)), apply('nodes', l))), add(a, apply('nodes', node(l, v, r))))),
                        hyp([0], eq(apply('aux', r, add(add(a, num(1)), apply('nodes', l))), add(add(add(a, num(1)), apply('nodes', l)), apply('nodes', r))),
                            eq(add(add(add(a, num(1)), apply('nodes', l)), apply('nodes', r)), add(a, apply('nodes', node(l, v, r))))),
                        ind('nodes_node', [1, 1], eq(add(add(add(a, num(1)), apply('nodes', l)), apply('nodes', r)), add(a, add(num(1), add(apply('nodes', l), apply('nodes', r)))))),
                    ],
                },
                {
                    title: 'Closing step 0 + nodes t = nodes t',
                    points: 5,
                    spec: { goalMode: 'EQUATION', graderTypes: ['EGGREGATE'], goal: eq(add(num(0), apply('nodes', t)), apply('nodes', t)) },
                    solution: [step('add_zero_left', [0], eq(apply('nodes', t), apply('nodes', t)))],
                },
            ],
        },
    ];
}

// Exercises that do not port, and the reason — printed at the end so the omission is explicit.
const EXCLUDED = [
    ['17209 Week 11 Quiz', 'multiple-choice quiz — a quiz exercise, not equational reasoning'],
    ['17210 Big Steps', 'big-step operational semantics: builds a derivation TREE over ⇒, not a rewrite chain over a term'],
    ['17211 Multiplication', 'termination proof via big-step — a property of the evaluation relation, not an equality'],
    ['17212 Threesum', 'termination + big-step correctness; the value claim needs a `threesum` definition the palette does not carry'],
    ['17213 Records', 'asks the student to EXTEND the semantics with new rules — outside a fixed rule palette'],
    ['17214 Computing Zero', 'termination with a nested induction over a non-structural measure'],
    ['17218 Week 12 Quiz', 'multiple-choice quiz about equational reasoning, not a derivation to grade'],
];

// ── HTTP helpers ─────────────────────────────────────────────────────────────
async function login(ctx, username) {
    const res = await ctx.post(`${BASE}/api/core/public/authenticate`, { data: { username, password: username, rememberMe: true } });
    if (!res.ok()) throw new Error(`login ${username} -> ${res.status()}`);
}

/**
 * Create the course. It reuses course 9018's group names on purpose: those groups already hold the
 * artemis_test_user_* logins from the e2e Liquibase seed, so the students are enrolled on creation
 * and can answer immediately.
 */
async function createCourse(ctx) {
    const course = {
        // The course overview sorts by title alone (foundation/util/course.util.ts `sortCourses`,
        // plain localeCompare, ascending) and the seeded e2e courses are all titled "E2E …".
        // Leading with "Abgabe" therefore puts this course at the TOP of the selection, ahead of
        // all 24 of them, instead of filing it under F.
        title: 'Abgabe FPV Equational Reasoning MiniOCaml',
        shortName: 'fpvmath' + Math.random().toString(36).slice(2, 6),
        description: 'The subset of the FPV MiniOCaml exercises that the Artemis math exercise type can express: '
            + 'accumulator-generalised lemmas proved by structural induction over N, lists and binary trees, '
            + 'certified by cvc5regate. See each exercise statement for the original claim it belongs to.',
        // Distinguishes the card from the uniformly-coloured seed courses.
        color: '#1E88E5',
        semester: 'Abgabe',
        testCourse: true,
        startDate: new Date(Date.now() - 3600e3).toISOString(),
        endDate: new Date(Date.now() + 365 * 24 * 3600e3).toISOString(),
        studentGroupName: 'artemis-e2eexercisepart-students',
        teachingAssistantGroupName: 'artemis-e2eexercisepart-tutors',
        editorGroupName: 'artemis-e2eexercisepart-editors',
        instructorGroupName: 'artemis-e2eexercisepart-instructors',
        courseInformationSharingConfiguration: 'DISABLED',
        maxPoints: 100,
        accuracyOfScores: 1,
        maxComplaints: 3,
        maxTeamComplaints: 3,
        maxComplaintTimeDays: 7,
        maxRequestMoreFeedbackTimeDays: 7,
        maxComplaintTextLimit: 2000,
        maxComplaintResponseTextLimit: 2000,
        onlineCourse: false,
        enrollmentEnabled: false,
        unenrollmentEnabled: false,
        presentationScore: 0,
    };
    const res = await ctx.post(`${BASE}/api/core/admin/courses`, {
        multipart: { course: { name: 'course', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(course)) } },
    });
    if (!res.ok()) throw new Error(`create course -> ${res.status()} ${(await res.text()).slice(0, 200)}`);
    return res.json();
}

async function createExercise(ctx, payload) {
    const res = await ctx.post(`${BASE}/api/math/math-exercises`, { data: payload });
    if (!res.ok()) throw new Error(`create "${payload.title}" -> ${res.status()} ${(await res.text()).slice(0, 200)}`);
    return res.json();
}

async function startParticipation(ctx, exerciseId) {
    const res = await ctx.post(`${BASE}/api/exercise/exercises/${exerciseId}/participations`);
    if (!res.ok() && res.status() !== 400 && res.status() !== 409) {
        throw new Error(`start ex=${exerciseId} -> ${res.status()} ${(await res.text()).slice(0, 120)}`);
    }
}

/** Submit one answer per problem of the exercise. */
async function submit(ctx, exerciseId, answers) {
    const res = await ctx.post(`${BASE}/api/math/exercises/${exerciseId}/math-submissions`, {
        data: {
            submitted: true,
            answers: answers.map(({ problemId, steps }) => ({
                problemId,
                steps: steps.map((s, i) => ({
                    stepIndex: i,
                    appliedRuleId: s.rule ?? null,
                    targetNodePath: s.path,
                    resultExpression: s.result,
                    direction: 'FORWARD',
                    derivationRole: s.role ?? 'MAIN',
                    kind: s.kind ?? 'A',
                    substitutionEquation: s.equation ?? null,
                })),
            })),
        },
    });
    if (!res.ok()) throw new Error(`submit ex=${exerciseId} -> ${res.status()} ${(await res.text()).slice(0, 200)}`);
    return res.json();
}

// ── main ─────────────────────────────────────────────────────────────────────
(async () => {
    const ctx = await request.newContext({ ignoreHTTPSErrors: true });
    await login(ctx, ADMIN);

    let courseId = EXISTING_COURSE;
    if (!courseId) {
        const course = await createCourse(ctx);
        courseId = course.id;
        console.log(`Created course ${courseId} "${course.title}" (${course.shortName}).`);
    } else {
        console.log(`Using existing course ${courseId}.`);
    }

    const items = catalogue();
    const created = [];
    for (const item of items) {
        const payload = {
            type: 'math',
            title: item.title,
            shortName: 'fpv' + Math.random().toString(36).slice(2, 10),
            courseId,
            maxPoints: item.problems.reduce((s, p) => s + p.points, 0),
            bonusPoints: 0,
            includedInOverallScore: 'INCLUDED_COMPLETELY',
            problemStatement: item.statement,
            presentationScoreEnabled: false,
            secondCorrectionEnabled: false,
            allowFeedbackRequests: false,
            allowComplaintsForAutomaticAssessments: true,
            manualDerivation: false,
            problems: item.problems.map((p) => ({
                title: p.title,
                points: p.points,
                goalMode: p.spec.goalMode,
                graderTypes: p.spec.graderTypes,
                sourceExpression: p.spec.source,
                targetExpression: p.spec.target,
                goalExpression: p.spec.goal,
                inductionVariable: p.spec.inductionVariable,
                inductionDatatype: p.spec.inductionDatatype ?? 'NAT',
                certifyingGraderType: p.spec.certifier,
                partialCreditEnabled: p.spec.partialCredit ?? false,
                acNormalization: p.spec.ac ?? false,
                manualDerivation: false,
                onlyShowApplicableRules: false,
                allowVerification: true,
                exampleDerivations: [],
            })),
        };
        try {
            const ex = await createExercise(ctx, payload);
            created.push({ ...item, exercise: ex });
            console.log(`  + ${item.title} (exercise ${ex.id}, ${ex.problems.length} problems)`);
        } catch (e) {
            console.log(`  ✗ ${item.title}: ${String(e.message).slice(0, 200)}`);
        }
    }

    if (WITH_SUBMISSIONS) {
        console.log('\nSubmitting the model derivations …');
        let i = 0;
        for (const c of created) {
            const answers = c.exercise.problems.map((p, k) => ({ problemId: p.id, steps: c.problems[k].solution }));
            try {
                await login(ctx, STUDENTS[i % STUDENTS.length]);
                await startParticipation(ctx, c.exercise.id);
                await submit(ctx, c.exercise.id, answers);
                console.log(`  ✓ ${STUDENTS[i % STUDENTS.length]} answered "${c.title}"`);
            } catch (e) {
                console.log(`  ✗ submit "${c.title}": ${String(e.message).slice(0, 200)}`);
            }
            i++;
        }
        await login(ctx, ADMIN);
    }

    console.log('\nNot ported from the FPV set:');
    for (const [name, why] of EXCLUDED) console.log(`  - ${name}: ${why}`);

    console.log(`\nDone. Course ${courseId}: ${BASE}/courses/${courseId}/exercises (student)`);
    console.log(`                       ${BASE}/course-management/${courseId} (instructor)`);
    await ctx.dispose();
})().catch((e) => {
    console.error(e);
    process.exit(1);
});
