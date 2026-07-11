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
 * Exponentiation {@code base^exponent}. An induction-mode operator: its recursive definitions (e.g.
 * {@code pow_zero}: {@code a^0 → 1}, {@code pow_succ}: {@code a^(S n) → a · a^n}) are instructor-authored
 * per problem and travel in the grade request as trusted {@code definitions}, so this block contributes no
 * global catalogue rules.
 */
@Lazy
@Conditional(MathEnabled.class)
@Component
public class PowBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "pow";
    }

    @Override
    public String getCategory() {
        return "induction";
    }

    @Override
    public String getLabel() {
        return "Power";
    }

    @Override
    public String getPaletteLatex() {
        return "a^{b}";
    }

    @Override
    public List<String> getSlots() {
        // Alphabetical slot order (the path encoding): base before exponent.
        return List.of("base", "exponent");
    }

    @Override
    public int getPrecedence() {
        // Binds tighter than · and unary −, matching standard convention a·b^c → a·(b^c).
        return 90;
    }

    @Override
    public LayoutCategory getLayoutCategory() {
        return LayoutCategory.POWER;
    }

    @Override
    public List<RewriteRule> getRules() {
        return List.of();
    }

    @Override
    public List<RewriteRule> getDefinitions() {
        var a = MathNodes.wc("a");
        var n = MathNodes.wc("n");
        // The recursive definition of exponentiation over ℕ, contributed in code and sent as trusted `definitions`.
        return List.of(new RewriteRule("pow_zero", "Power of zero", "a^0 \\to 1", MathNodes.pow(a, MathNodes.num("0")), MathNodes.num("1"), RuleDirection.FORWARD_ONLY),
                new RewriteRule("pow_succ", "Power successor", "a^{S(n)} \\to a \\cdot a^n", MathNodes.pow(a, MathNodes.succ(n)), MathNodes.mul(a, MathNodes.pow(a, n)),
                        RuleDirection.FORWARD_ONLY));
    }
}
