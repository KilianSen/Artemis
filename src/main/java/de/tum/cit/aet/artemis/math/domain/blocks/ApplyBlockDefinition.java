package de.tum.cit.aet.artemis.math.domain.blocks;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.BlockDefinition;
import de.tum.cit.aet.artemis.math.domain.LayoutCategory;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;
import de.tum.cit.aet.artemis.math.domain.RuleDirection;

/**
 * A named, n-ary function application {@code f(a₁, …, aₖ)}, matching the Regate protocol's {@code apply}
 * term (see {@code GRADING_PROTOCOL.md}). The node carries the function name in {@link de.tum.cit.aet.artemis.math.domain.MathNode#getValue() value}
 * and its arguments in the single ordered {@code args} slot.
 * <p>
 * Like {@link SuccBlockDefinition} and {@link PowBlockDefinition} it carries no catalogue rules — a function's
 * behaviour is supplied per exercise as trusted recursive {@code definitions} and certified by a Regate
 * induction backend. This block exists so the registry can render and round-trip the term; instructors author
 * concrete functions (e.g. {@code fact}, {@code fact_aux}) as definitions, not as new blocks.
 */
@Lazy
@Conditional(MathEnabled.class)
@Component
public class ApplyBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "apply";
    }

    @Override
    public String getCategory() {
        return "induction";
    }

    @Override
    public String getLabel() {
        return "Function application";
    }

    @Override
    public String getPaletteLatex() {
        return "f(a)";
    }

    @Override
    public List<String> getSlots() {
        return List.of("args");
    }

    @Override
    public int getPrecedence() {
        // A function application binds as tightly as a terminal; it renders its own parentheses.
        return 95;
    }

    @Override
    public LayoutCategory getLayoutCategory() {
        return LayoutCategory.FUNCTION_APP;
    }

    @Override
    public List<RewriteRule> getRules() {
        return List.of();
    }

    /**
     * TEMPORARY SHIM (A-M1): ships the recursive definitions of {@code fact} and {@code fact_aux} for the
     * "What The Fact" exercise ({@code fact_aux x n = x · fact n}), so 17215 grades end-to-end before per-problem
     * function-definition authoring exists. Like {@link PowBlockDefinition}'s {@code pow_*}, these travel globally
     * in every induction request's trusted {@code definitions}; a backend uses only the ones its goal references.
     * <p>
     * Replace with per-problem {@code definitions} on {@code MathProblem} (plan option b): a generic {@code apply}
     * block must not hard-code specific functions.
     * <ul>
     * <li>ℕ: {@code fact(0) → 1}, {@code fact(S k) → (S k) · fact(k)}; {@code fact_aux(x, 0) → x},
     * {@code fact_aux(x, S k) → fact_aux(x · (S k), k)}</li>
     * <li>list (A-M2): {@code summa(nil) → 0}, {@code summa(cons h t) → h + summa(t)}; {@code sum(nil, a) → a},
     * {@code sum(cons h t, a) → sum(t, a + h)} — {@code nil}/{@code cons} are {@code apply} nodes.</li>
     * <li>tree (A-M3): {@code nodes(empty) → 0}, {@code nodes(node l v r) → 1 + (nodes(l) + nodes(r))};
     * {@code aux(empty, a) → a}, {@code aux(node l v r, a) → aux(r, aux(l, a + 1))} — {@code empty}/{@code node} are
     * {@code apply} nodes; {@code node} has two recursive fields, yielding two induction hypotheses.</li>
     * </ul>
     */
    @Override
    public List<RewriteRule> getDefinitions() {
        var x = MathNodes.wc("x");
        var k = MathNodes.wc("k");
        var h = MathNodes.wc("h");
        var t = MathNodes.wc("t");
        var a = MathNodes.wc("a");
        var l = MathNodes.wc("l");
        var v = MathNodes.wc("v");
        var r = MathNodes.wc("r");
        return List.of(
                new RewriteRule("fact_zero", "Factorial of zero", "\\mathrm{fact}\\,0 \\to 1", MathNodes.apply("fact", MathNodes.num("0")), MathNodes.num("1"),
                        RuleDirection.FORWARD_ONLY),
                new RewriteRule("fact_succ", "Factorial successor", "\\mathrm{fact}\\,S(k) \\to S(k) \\cdot \\mathrm{fact}\\,k", MathNodes.apply("fact", MathNodes.succ(k)),
                        MathNodes.mul(MathNodes.succ(k), MathNodes.apply("fact", k)), RuleDirection.FORWARD_ONLY),
                new RewriteRule("fact_aux_zero", "Accumulator base", "\\mathrm{fact\\_aux}\\,x\\,0 \\to x", MathNodes.apply("fact_aux", x, MathNodes.num("0")), x,
                        RuleDirection.FORWARD_ONLY),
                new RewriteRule("fact_aux_succ", "Accumulator step", "\\mathrm{fact\\_aux}\\,x\\,S(k) \\to \\mathrm{fact\\_aux}\\,(x \\cdot S(k))\\,k",
                        MathNodes.apply("fact_aux", x, MathNodes.succ(k)), MathNodes.apply("fact_aux", MathNodes.mul(x, MathNodes.succ(k)), k), RuleDirection.FORWARD_ONLY),
                // List sum (A-M2): nil/cons travel as apply nodes.
                new RewriteRule("summa_nil", "Sum of empty list", "\\mathrm{summa}\\,\\mathrm{nil} \\to 0", MathNodes.apply("summa", MathNodes.apply("nil")), MathNodes.num("0"),
                        RuleDirection.FORWARD_ONLY),
                new RewriteRule("summa_cons", "Sum of cons", "\\mathrm{summa}\\,(\\mathrm{cons}\\,h\\,t) \\to h + \\mathrm{summa}\\,t",
                        MathNodes.apply("summa", MathNodes.apply("cons", h, t)), MathNodes.add(h, MathNodes.apply("summa", t)), RuleDirection.FORWARD_ONLY),
                new RewriteRule("sum_nil", "Accumulating sum base", "\\mathrm{sum}\\,\\mathrm{nil}\\,a \\to a", MathNodes.apply("sum", MathNodes.apply("nil"), a), a,
                        RuleDirection.FORWARD_ONLY),
                new RewriteRule("sum_cons", "Accumulating sum step", "\\mathrm{sum}\\,(\\mathrm{cons}\\,h\\,t)\\,a \\to \\mathrm{sum}\\,t\\,(a + h)",
                        MathNodes.apply("sum", MathNodes.apply("cons", h, t), a), MathNodes.apply("sum", t, MathNodes.add(a, h)), RuleDirection.FORWARD_ONLY),
                // Binary-tree node count (A-M3): empty/node travel as apply nodes; node has two recursive fields l, r.
                new RewriteRule("nodes_empty", "Nodes of empty tree", "\\mathrm{nodes}\\,\\mathrm{empty} \\to 0", MathNodes.apply("nodes", MathNodes.apply("empty")),
                        MathNodes.num("0"), RuleDirection.FORWARD_ONLY),
                new RewriteRule("nodes_node", "Nodes of a node", "\\mathrm{nodes}\\,(\\mathrm{node}\\,l\\,v\\,r) \\to 1 + (\\mathrm{nodes}\\,l + \\mathrm{nodes}\\,r)",
                        MathNodes.apply("nodes", MathNodes.apply("node", l, v, r)),
                        MathNodes.add(MathNodes.num("1"), MathNodes.add(MathNodes.apply("nodes", l), MathNodes.apply("nodes", r))), RuleDirection.FORWARD_ONLY),
                new RewriteRule("aux_empty", "Node-count accumulator base", "\\mathrm{aux}\\,\\mathrm{empty}\\,a \\to a", MathNodes.apply("aux", MathNodes.apply("empty"), a), a,
                        RuleDirection.FORWARD_ONLY),
                new RewriteRule("aux_node", "Node-count accumulator step",
                        "\\mathrm{aux}\\,(\\mathrm{node}\\,l\\,v\\,r)\\,a \\to \\mathrm{aux}\\,r\\,(\\mathrm{aux}\\,l\\,(a + 1))",
                        MathNodes.apply("aux", MathNodes.apply("node", l, v, r), a), MathNodes.apply("aux", r, MathNodes.apply("aux", l, MathNodes.add(a, MathNodes.num("1")))),
                        RuleDirection.FORWARD_ONLY));
    }
}
