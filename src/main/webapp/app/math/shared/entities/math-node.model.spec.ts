import { describe, expect, it } from 'vitest';
import {
    MathNode,
    applyRule,
    assertWildcardFree,
    distance,
    equalsAC,
    isTautology,
    mathNodeToLatex,
    mathNodesEqual,
    normalize,
    normalizeAC,
    size,
    wildcardizeExcept,
} from 'app/math/shared/entities/math-node.model';
import { RuleConstraint } from 'app/math/shared/entities/rule-constraint.model';

const num = (v: string): MathNode => ({ type: 'number', value: v });
const variable = (v: string): MathNode => ({ type: 'variable', value: v });
const wc = (v: string): MathNode => ({ type: 'wild', value: v });
const add = (l: MathNode, r: MathNode): MathNode => ({ type: 'add', slots: { left: [l], right: [r] } });
const mul = (l: MathNode, r: MathNode): MathNode => ({ type: 'mul', slots: { left: [l], right: [r] } });
const frac = (n: MathNode, d: MathNode): MathNode => ({ type: 'frac', slots: { numerator: [n], denominator: [d] } });
const neg = (inner: MathNode): MathNode => ({ type: 'neg', slots: { inner: [inner] } });
const eq = (l: MathNode, r: MathNode): MathNode => ({ type: 'eq', slots: { left: [l], right: [r] } });

describe('math-node engine — frontend mirror', () => {
    describe('applyRule', () => {
        it('applies add_zero_left at root', () => {
            // pattern: 0 + a, template: a
            const pattern = add(num('0'), wc('a'));
            const template = wc('a');
            const tree = add(num('0'), variable('x'));
            expect(applyRule(tree, [], pattern, template)).toEqual(variable('x'));
        });

        it('add_comm applied twice is identity', () => {
            const pattern = add(wc('a'), wc('b'));
            const template = add(wc('b'), wc('a'));
            const tree = add(variable('a'), variable('b'));
            const once = applyRule(tree, [], pattern, template)!;
            const twice = applyRule(once, [], pattern, template)!;
            expect(mathNodesEqual(twice, tree)).toBe(true);
        });

        it('mul_zero_left collapses any rhs to 0', () => {
            const pattern = mul(num('0'), wc('a'));
            const template = num('0');
            const tree = mul(num('0'), add(variable('a'), variable('b')));
            expect(applyRule(tree, [], pattern, template)).toEqual(num('0'));
        });

        it('returns undefined on pattern mismatch', () => {
            const pattern = add(num('0'), wc('a'));
            const template = wc('a');
            const tree = add(variable('x'), num('0')); // wrong order
            expect(applyRule(tree, [], pattern, template)).toBeUndefined();
        });

        it('enforces non-linear binding consistency (cancel rule)', () => {
            // pattern: (c · a) / (c · b) — same wildcard c on both sides
            const pattern = frac(mul(wc('c'), wc('a')), mul(wc('c'), wc('b')));
            const template = frac(wc('a'), wc('b'));

            const matching = frac(mul(variable('x'), variable('a')), mul(variable('x'), variable('b')));
            expect(applyRule(matching, [], pattern, template)).toEqual(frac(variable('a'), variable('b')));

            const nonMatching = frac(mul(variable('x'), variable('a')), mul(variable('y'), variable('b')));
            expect(applyRule(nonMatching, [], pattern, template)).toBeUndefined();
        });

        it('side condition c != 0 rejects zero factor in cancel rule', () => {
            const pattern = frac(mul(wc('c'), wc('a')), mul(wc('c'), wc('b')));
            const template = frac(wc('a'), wc('b'));
            const constraints: RuleConstraint[] = [{ type: 'NOT_EQUAL_TO_CONSTANT', wildcardName: 'c', value: num('0') }];

            const zeroFactor = frac(mul(num('0'), variable('a')), mul(num('0'), variable('b')));
            expect(applyRule(zeroFactor, [], pattern, template, constraints)).toBeUndefined();

            const nonZeroFactor = frac(mul(num('2'), variable('a')), mul(num('2'), variable('b')));
            expect(applyRule(nonZeroFactor, [], pattern, template, constraints)).toEqual(frac(variable('a'), variable('b')));
        });
    });

    describe('normalize', () => {
        it('collapses numeric forms', () => {
            expect(normalize(num('0.0'))).toEqual(num('0'));
            expect(normalize(num('00'))).toEqual(num('0'));
            expect(normalize(num('-0'))).toEqual(num('0'));
            expect(normalize(num('1.50'))).toEqual(num('1.5'));
        });

        it('preserves non-numeric values', () => {
            expect(normalize(variable('x'))).toEqual(variable('x'));
            expect(normalize(num('notANumber'))).toEqual(num('notANumber'));
        });

        it('recurses through slots', () => {
            expect(normalize(add(num('0.0'), variable(' x ')))).toEqual(add(num('0'), variable('x')));
        });

        it('returns undefined unchanged', () => {
            expect(normalize(undefined)).toBeUndefined();
        });
    });

    describe('assertWildcardFree', () => {
        it('rejects a wildcard at the root', () => {
            expect(() => assertWildcardFree(wc('a'))).toThrow(/Wildcard/);
        });

        it('rejects a wildcard nested deep', () => {
            expect(() => assertWildcardFree(add(variable('x'), mul(num('1'), wc('c'))))).toThrow(/Wildcard/);
        });

        it('accepts a wildcard-free tree', () => {
            expect(() => assertWildcardFree(add(variable('x'), mul(num('1'), variable('y'))))).not.toThrow();
        });

        it('accepts undefined', () => {
            expect(() => assertWildcardFree(undefined)).not.toThrow();
        });
    });

    describe('rule direction', () => {
        it('REVERSE on a BIDIRECTIONAL rule swaps pattern and template', () => {
            // add_assoc: (a + b) + c ↔ a + (b + c)
            const pattern = add(add(wc('a'), wc('b')), wc('c'));
            const template = add(wc('a'), add(wc('b'), wc('c')));
            const grouped = add(variable('a'), add(variable('b'), variable('c'))); // RHS shape
            const result = applyRule(grouped, [], pattern, template, [], 'REVERSE', 'BIDIRECTIONAL');
            expect(result).toEqual(add(add(variable('a'), variable('b')), variable('c')));
        });

        it('REVERSE on a FORWARD_ONLY rule returns undefined', () => {
            // add_zero_left: 0 + a → a (forward only)
            const pattern = add(num('0'), wc('a'));
            const template = wc('a');
            expect(applyRule(variable('x'), [], pattern, template, [], 'REVERSE', 'FORWARD_ONLY')).toBeUndefined();
        });

        it('FORWARD on a FORWARD_ONLY rule still works', () => {
            const pattern = add(num('0'), wc('a'));
            const template = wc('a');
            expect(applyRule(add(num('0'), variable('x')), [], pattern, template, [], 'FORWARD', 'FORWARD_ONLY')).toEqual(variable('x'));
        });
    });

    describe('distributivity and negation', () => {
        it('mul_distrib expands forward', () => {
            const pattern = mul(wc('a'), add(wc('b'), wc('c')));
            const template = add(mul(wc('a'), wc('b')), mul(wc('a'), wc('c')));
            const tree = mul(variable('x'), add(variable('y'), variable('z')));
            expect(applyRule(tree, [], pattern, template, [], 'FORWARD', 'BIDIRECTIONAL')).toEqual(add(mul(variable('x'), variable('y')), mul(variable('x'), variable('z'))));
        });

        it('mul_distrib factors in reverse', () => {
            const pattern = mul(wc('a'), add(wc('b'), wc('c')));
            const template = add(mul(wc('a'), wc('b')), mul(wc('a'), wc('c')));
            const expanded = add(mul(variable('x'), variable('y')), mul(variable('x'), variable('z')));
            expect(applyRule(expanded, [], pattern, template, [], 'REVERSE', 'BIDIRECTIONAL')).toEqual(mul(variable('x'), add(variable('y'), variable('z'))));
        });

        it('neg_neg roundtrip', () => {
            const pattern = neg(neg(wc('a')));
            const template = wc('a');
            const collapsed = applyRule(neg(neg(variable('y'))), [], pattern, template, [], 'FORWARD', 'BIDIRECTIONAL')!;
            expect(collapsed).toEqual(variable('y'));
            const reintroduced = applyRule(collapsed, [], pattern, template, [], 'REVERSE', 'BIDIRECTIONAL')!;
            expect(reintroduced).toEqual(neg(neg(variable('y'))));
        });
    });

    describe('isTautology', () => {
        it('returns true for an equality with structurally equal sides', () => {
            expect(isTautology(eq(variable('x'), variable('x')))).toBe(true);
        });

        it('returns false for an equality with different sides', () => {
            expect(isTautology(eq(variable('x'), variable('y')))).toBe(false);
        });

        it('returns false for a non-equality root', () => {
            expect(isTautology(add(variable('x'), variable('x')))).toBe(false);
        });
    });

    describe('distance', () => {
        it('returns 0 for structurally equal trees', () => {
            expect(distance(add(variable('a'), variable('b')), add(variable('a'), variable('b')))).toBe(0);
        });

        it('is symmetric', () => {
            const a = add(num('0'), variable('x'));
            const b = variable('x');
            expect(distance(a, b)).toBe(distance(b, a));
        });

        it('decreases when applying add_zero_left toward target', () => {
            const source = add(num('0'), variable('x'));
            const target = variable('x');
            const before = distance(source, target);
            const after = distance(target, target);
            expect(after).toBeLessThan(before);
            expect(after).toBe(0);
        });

        it('stays the same on commutativity (no progress)', () => {
            const source = add(variable('a'), variable('b'));
            const swapped = add(variable('b'), variable('a'));
            const target = variable('x');
            expect(distance(source, target)).toBe(distance(swapped, target));
        });
    });

    describe('AC normalisation', () => {
        it('commuted add normalises to the same form', () => {
            expect(normalizeAC(add(variable('a'), variable('b')))).toEqual(normalizeAC(add(variable('b'), variable('a'))));
        });

        it('different associativity normalises to the same form', () => {
            const leftAssoc = add(add(variable('c'), variable('a')), variable('b'));
            const rightAssoc = add(variable('b'), add(variable('a'), variable('c')));
            expect(normalizeAC(leftAssoc)).toEqual(normalizeAC(rightAssoc));
        });

        it('equalsAC respects the ac flag', () => {
            const ab = add(variable('a'), variable('b'));
            const ba = add(variable('b'), variable('a'));
            expect(equalsAC(ab, ba, true)).toBe(true);
            expect(equalsAC(ab, ba, false)).toBe(false);
        });

        it('AC normalisation makes a + b = b + a a tautology', () => {
            const goal: MathNode = { type: 'eq', slots: { left: [add(variable('a'), variable('b'))], right: [add(variable('b'), variable('a'))] } };
            expect(isTautology(normalizeAC(goal)!)).toBe(true);
            expect(isTautology(goal)).toBe(false);
        });
    });

    describe('size', () => {
        it('counts every node in the tree', () => {
            expect(size(variable('x'))).toBe(1);
            expect(size(add(variable('x'), num('0')))).toBe(3);
            expect(size(add(add(variable('x'), num('0')), variable('y')))).toBe(5);
        });
    });

    describe('equation-mode reduction (drives the EQUATION grader)', () => {
        it('reaches a tautology by applying add_comm on one side', () => {
            // goal: a + b = b + a
            const goal = eq(add(variable('a'), variable('b')), add(variable('b'), variable('a')));
            // path [0] = left slot of equality (alphabetical sort: left < right)
            const pattern = add(wc('a'), wc('b'));
            const template = add(wc('b'), wc('a'));
            const reduced = applyRule(goal, [0], pattern, template, [], 'FORWARD', 'BIDIRECTIONAL');
            expect(reduced).toBeDefined();
            expect(isTautology(reduced!)).toBe(true);
        });
    });

    describe('wildcardizeExcept (schematic induction hypothesis, C2)', () => {
        const apply = (name: string, ...args: MathNode[]): MathNode => ({ type: 'apply', value: name, slots: { args } });

        it('turns every free variable except the kept one into a wildcard', () => {
            // fact_aux(x, n) = x · fact(n), keep n
            const goal = eq(apply('fact_aux', variable('x'), variable('n')), mul(variable('x'), apply('fact', variable('n'))));
            const schema = wildcardizeExcept(goal, 'n');
            expect(schema).toEqual(eq(apply('fact_aux', wc('x'), variable('n')), mul(wc('x'), apply('fact', variable('n')))));
        });

        it('leaves a goal with no accumulator unchanged (no regression for plain ℕ induction)', () => {
            const goal = eq(apply('f', variable('n')), num('1'));
            expect(wildcardizeExcept(goal, 'n')).toEqual(goal);
        });

        it('does not touch function names (they live in apply.value, not variable nodes)', () => {
            const schema = wildcardizeExcept(apply('g', variable('a')), 'n');
            expect(schema).toEqual(apply('g', wc('a')));
        });
    });

    describe('mathNodeToLatex — apply', () => {
        const apply = (name: string, ...args: MathNode[]): MathNode => ({ type: 'apply', value: name, slots: { args } });

        it('renders a named application as juxtaposition, escaping underscores', () => {
            expect(mathNodeToLatex(apply('fact_aux', variable('x'), variable('n')))).toBe('\\mathrm{fact\\_aux}\\,x\\,n');
        });

        it('parenthesises a compound argument so f (g x) never collapses to f g x', () => {
            expect(mathNodeToLatex(apply('f', apply('g', variable('x'))))).toBe('\\mathrm{f}\\,\\left(\\mathrm{g}\\,x\\right)');
        });

        it('renders a nullary application as just the name', () => {
            expect(mathNodeToLatex(apply('unit'))).toBe('\\mathrm{unit}');
        });

        // The registry↔apply bridge: a block may declare `functionName`, so a new operator travels the wire
        // as the generic `apply` node every grading backend already compiles from `definitions`, while still
        // rendering as an operator. Without this, a new operator needs a new MathNode type, which every
        // backend must learn before it can grade anything using it.
        describe('operator emitted as apply (registry bridge)', () => {
            // Terminals carry a high precedence in the real registry; without them every child would be
            // parenthesised, which would make these assertions test the stub rather than the bridge.
            const terminals: Record<string, { precedence: number; layoutCategory: string }> = {
                variable: { precedence: 100, layoutCategory: 'TERMINAL_VARIABLE' },
                number: { precedence: 100, layoutCategory: 'TERMINAL_NUMBER' },
            };
            const registry =
                (blocks: Record<string, { functionName?: string; latexSymbol?: string; precedence?: number; layoutCategory?: string }>) => (type: string, value?: string) =>
                    (value !== undefined ? blocks[value] : undefined) ?? blocks[type] ?? terminals[type];
            const infix = (functionName: string, latexSymbol: string, precedence = 40) =>
                registry({ [functionName]: { functionName, latexSymbol, precedence, layoutCategory: 'BINARY_INFIX' } }) as Parameters<typeof mathNodeToLatex>[1];

            it('renders infix when a block claims the function name', () => {
                const lookup = infix('oplus', '\\oplus');
                expect(mathNodeToLatex(apply('oplus', variable('a'), variable('b')), lookup)).toBe('a \\oplus b');
            });

            it('renders prefix for a unary operator', () => {
                const lookup = registry({ lnot: { functionName: 'lnot', latexSymbol: '\\neg', precedence: 80, layoutCategory: 'UNARY_PREFIX' } }) as Parameters<
                    typeof mathNodeToLatex
                >[1];
                expect(mathNodeToLatex(apply('lnot', variable('p')), lookup)).toBe('\\neg p');
            });

            it('still parenthesises by precedence inside the infix form', () => {
                const lookup = registry({
                    oplus: { functionName: 'oplus', latexSymbol: '\\oplus', precedence: 40, layoutCategory: 'BINARY_INFIX' },
                    add: { precedence: 30, layoutCategory: 'BINARY_INFIX', latexSymbol: '+' },
                }) as Parameters<typeof mathNodeToLatex>[1];
                const nested: MathNode = { type: 'add', slots: { left: [variable('x')], right: [variable('y')] } };
                expect(mathNodeToLatex(apply('oplus', nested, variable('b')), lookup)).toBe('\\left(x + y\\right) \\oplus b');
            });

            it('falls back to juxtaposition when the arity does not match the layout', () => {
                const lookup = infix('oplus', '\\oplus');
                // Declared BINARY_INFIX but applied to three arguments — render as a function, never mis-render.
                expect(mathNodeToLatex(apply('oplus', variable('a'), variable('b'), variable('c')), lookup)).toBe('\\mathrm{oplus}\\,a\\,b\\,c');
            });

            it('leaves unrelated functions as juxtaposition', () => {
                const lookup = infix('oplus', '\\oplus');
                expect(mathNodeToLatex(apply('fact', variable('n')), lookup)).toBe('\\mathrm{fact}\\,n');
            });
        });
    });

    // Parity fixtures — these MUST be kept in sync with PathCheckerGraderTest.java on the backend
    // (the anchor previously named MathGradingServiceTest.java, which does not exist — so nothing
    // was actually being kept in sync). The no-regress case below is the one that mattered: the
    // client offered a commutativity step under AC that the server's replay() treated as a revisit,
    // truncating a derivation that reached the goal. See replay_acOn_commutativityStepDoesNotBreakTheChain.
    describe('parity with backend engine', () => {
        const cases: { name: string; tree: MathNode; path: number[]; pattern: MathNode; template: MathNode; expected: MathNode | undefined }[] = [
            { name: 'add_zero_left at root', tree: add(num('0'), variable('x')), path: [], pattern: add(num('0'), wc('a')), template: wc('a'), expected: variable('x') },
            { name: 'add_zero_left mismatch', tree: add(variable('x'), num('0')), path: [], pattern: add(num('0'), wc('a')), template: wc('a'), expected: undefined },
            // Pairs with PathCheckerGraderTest.replay_acOn_commutativityStepDoesNotBreakTheChain: mul_distrib's
            // pattern is `a * (b + c)`, matched STRUCTURALLY, so `(b + c) * a` must be reordered first. That is
            // why an AC reordering is a real step in both engines and must not count as revisiting a state.
            {
                name: 'mul_distrib needs the operands reordered first (structural match, not AC)',
                tree: mul(add(variable('b'), variable('c')), variable('a')),
                path: [],
                pattern: mul(wc('a'), add(wc('b'), wc('c'))),
                template: add(mul(wc('a'), wc('b')), mul(wc('a'), wc('c'))),
                expected: undefined,
            },
            {
                name: 'mul_distrib applies once the reordering has happened',
                tree: mul(variable('a'), add(variable('b'), variable('c'))),
                path: [],
                pattern: mul(wc('a'), add(wc('b'), wc('c'))),
                template: add(mul(wc('a'), wc('b')), mul(wc('a'), wc('c'))),
                expected: add(mul(variable('a'), variable('b')), mul(variable('a'), variable('c'))),
            },
        ];
        for (const c of cases) {
            it(c.name, () => {
                expect(applyRule(c.tree, c.path, c.pattern, c.template)).toEqual(c.expected);
            });
        }
    });
});
