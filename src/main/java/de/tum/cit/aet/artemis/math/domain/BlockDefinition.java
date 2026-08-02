package de.tum.cit.aet.artemis.math.domain;

import java.util.List;

/**
 * A block type available in the math editor palette.
 * Implementations are Spring {@code @Component} beans collected by {@link de.tum.cit.aet.artemis.math.service.BlockRegistry}.
 * New block types are added by implementing this interface — no schema changes are required.
 */
public interface BlockDefinition {

    /**
     * @return the node type string used in {@link MathNode#getType()}
     */
    String getType();

    /**
     * @return the grouping category for display in the palette (e.g., {@code "arithmetic"})
     */
    String getCategory();

    /**
     * @return the human-readable display name
     */
    String getLabel();

    /**
     * @return the LaTeX string representing this block in the palette
     */
    String getPaletteLatex();

    /**
     * @return the ordered list of named slot names that child nodes can be placed in
     */
    List<String> getSlots();

    /**
     * @return the rewrite rules owned by this block type
     */
    List<RewriteRule> getRules();

    /**
     * Recursive/definitional rewrite rules contributed by this block for INDUCTION mode (e.g. {@code pow_succ}:
     * {@code a^(S n) → a · a^n}). Unlike {@link #getRules() catalogue rules} these are <em>trusted</em>
     * definitions that travel in the grade request's {@code definitions} field. They are contributed in code —
     * never authored per problem.
     *
     * @return the block's recursive definitions, or an empty list
     */
    default List<RewriteRule> getDefinitions() {
        return List.of();
    }

    /**
     * Operator precedence for auto-parenthesization. Higher value binds tighter.
     * Terminals should return a high value (e.g., 100); unknown types default to 0.
     *
     * @return the operator precedence
     */
    default int getPrecedence() {
        return 0;
    }

    /**
     * @return the associativity used to determine when the right child needs parentheses
     */
    default Associativity getAssociativity() {
        return Associativity.NONE;
    }

    /**
     * Rendering layout category understood by the frontend.
     * All implementations must declare this explicitly so new node types
     * are never silently assigned the wrong rendering.
     *
     * @return the layout category for this block type
     */
    LayoutCategory getLayoutCategory();

    /**
     * Unicode symbol displayed in the interactive editor for {@code BINARY_INFIX} nodes.
     *
     * @return the display symbol, or {@code null} for non-infix layout categories
     */
    default String getDisplaySymbol() {
        return null;
    }

    /**
     * The function name this block emits as, decoupling how a block <em>renders</em> from what it
     * <em>emits</em>.
     * <p>
     * A block normally emits nodes of its own {@link #getType() type}, which means a new operator is a new
     * MathNode type — and a node type no grading backend knows is declined (see {@code GRADING_PROTOCOL.md},
     * "Unimplemented vocabulary"), so adding one costs a release in every backend. When this method returns a
     * non-null name, the block instead emits the protocol's generic {@code apply} node carrying that name,
     * with its arguments in the ordered {@code args} slot — the shape every backend already compiles from the
     * exercise's trusted {@code definitions}. The block keeps its own {@link #getLayoutCategory()} and
     * {@link #getLatexSymbol()}, so an operator can still render infix (e.g. {@code a ⊕ b}) while travelling
     * as {@code apply("oplus", a, b)}.
     * <p>
     * What this buys: a numeric operator declared this way costs <em>one reviewed block bean here</em> — no
     * protocol version, and no change in any grading backend. It does <em>not</em> make operators authorable
     * at runtime, and is not meant to: {@code definitions} are unconditionally trusted by every backend (see
     * {@code GRADING_PROTOCOL.md}, "Recursive definitions are definitional and therefore always trusted, in
     * every mode and whatever {@code verify_rules} says"), so a defining equation must arrive through code
     * review like any other operation or rule. This method moves the cost from four backends to one bean; it
     * does not move authorship out of code.
     * <p>
     * Note it buys translation, not automation: an operator whose semantics the target kernel cannot reason
     * about still grades {@code unknown}, and an operator needing a sort the term language lacks (a boolean,
     * say) cannot be expressed this way at all.
     * <p>
     * {@link #getSlots()} still declares the block's rendering slots; their count is the function's arity.
     *
     * @return the {@code apply} function name to emit, or {@code null} to emit this block's own node type
     */
    default String getFunctionName() {
        return null;
    }

    /**
     * LaTeX symbol emitted in math output for {@code BINARY_INFIX} nodes (e.g., {@code "\\cdot"}).
     *
     * @return the LaTeX symbol, or {@code null} for non-infix layout categories
     */
    default String getLatexSymbol() {
        return null;
    }
}
