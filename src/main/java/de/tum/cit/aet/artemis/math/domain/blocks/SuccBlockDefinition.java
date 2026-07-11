package de.tum.cit.aet.artemis.math.domain.blocks;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.BlockDefinition;
import de.tum.cit.aet.artemis.math.domain.LayoutCategory;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;

/**
 * The natural-number successor {@code S(n)}. Appears in induction obligations (the goal instantiated at
 * {@code S n} in the inductive step). Like {@link PowBlockDefinition} it carries no global rules — successor
 * semantics live in the proof kernel of a formal Regate backend.
 */
@Lazy
@Conditional(MathEnabled.class)
@Component
public class SuccBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "succ";
    }

    @Override
    public String getCategory() {
        return "induction";
    }

    @Override
    public String getLabel() {
        return "Successor";
    }

    @Override
    public String getPaletteLatex() {
        return "S(a)";
    }

    @Override
    public List<String> getSlots() {
        return List.of("inner");
    }

    @Override
    public int getPrecedence() {
        // A constructor application, effectively terminal-tight; it renders with its own parentheses.
        return 95;
    }

    @Override
    public LayoutCategory getLayoutCategory() {
        return LayoutCategory.SUCCESSOR;
    }

    @Override
    public List<RewriteRule> getRules() {
        return List.of();
    }
}
