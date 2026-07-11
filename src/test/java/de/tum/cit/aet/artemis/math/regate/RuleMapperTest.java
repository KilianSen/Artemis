package de.tum.cit.aet.artemis.math.regate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.NotEqualToConstant;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;
import de.tum.cit.aet.artemis.math.domain.RuleDirection;
import de.tum.cit.aet.artemis.math.regate.dto.RuleSpec;

/**
 * Pure unit tests for the Artemis rule → Regate protocol {@code Rule} mapping. No Spring context.
 */
class RuleMapperTest {

    @Test
    void mapsFractionCancellationWithNonzeroGuard() {
        // (c·a)/(c·b) → a/b, guarded by c != 0
        RewriteRule rule = new RewriteRule("frac_mul_cancel_left", "Cancel", "\\frac{c a}{c b} \\to \\frac{a}{b}",
                MathNodes.frac(MathNodes.mul(MathNodes.wc("c"), MathNodes.wc("a")), MathNodes.mul(MathNodes.wc("c"), MathNodes.wc("b"))),
                MathNodes.frac(MathNodes.wc("a"), MathNodes.wc("b")), RuleDirection.FORWARD_ONLY, List.of(new NotEqualToConstant("c", MathNodes.num("0"))));

        RuleSpec spec = RuleMapper.toRuleSpec(rule, "frac");

        assertThat(spec.id()).isEqualTo("frac_mul_cancel_left");
        assertThat(spec.owner()).isEqualTo("frac");
        assertThat(spec.bidirectional()).isFalse();
        // types translated to protocol vocabulary: fraction → frac, wildcard → wild
        assertThat(spec.lhs().getType()).isEqualTo("frac");
        assertThat(spec.lhs().getSlots().get("numerator").getFirst().getSlots().get("left").getFirst().getType()).isEqualTo("wild");
        assertThat(spec.rhs().getType()).isEqualTo("frac");
        // c != 0 → protocol `nonzero` on wildcard c
        assertThat(spec.conditions()).singleElement().satisfies(cond -> {
            assertThat(cond.kind()).isEqualTo("nonzero");
            assertThat(cond.variable()).isEqualTo("c");
            assertThat(cond.arg()).isNull();
        });
    }

    @Test
    void mapsBidirectionalRuleWithNoConditions() {
        // a + b → b + a, bidirectional, no guards
        RewriteRule rule = new RewriteRule("add_comm", "Commutativity", "a + b \\to b + a", MathNodes.add(MathNodes.wc("a"), MathNodes.wc("b")),
                MathNodes.add(MathNodes.wc("b"), MathNodes.wc("a")), RuleDirection.BIDIRECTIONAL);

        RuleSpec spec = RuleMapper.toRuleSpec(rule, "add");

        assertThat(spec.bidirectional()).isTrue();
        assertThat(spec.conditions()).isEmpty();
        assertThat(spec.lhs().getType()).isEqualTo("add");
    }

    @Test
    void rejectsNotEqualToNonZeroConstant() {
        RewriteRule rule = new RewriteRule("bogus", "Bogus", "", MathNodes.wc("a"), MathNodes.wc("a"), RuleDirection.FORWARD_ONLY,
                List.of(new NotEqualToConstant("a", MathNodes.num("1"))));

        assertThatThrownBy(() -> RuleMapper.toRuleSpec(rule, "x")).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("nonzero");
    }
}
