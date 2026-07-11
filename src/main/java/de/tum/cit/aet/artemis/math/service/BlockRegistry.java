package de.tum.cit.aet.artemis.math.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.BlockDefinition;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;

/**
 * Collects all {@link BlockDefinition} Spring beans at startup and indexes their rules.
 * New block types are contributed via code — no database changes required.
 * <p>
 * At init time, every rule's {@code pattern} and {@code template} is normalised via
 * {@link MathNodes#normalize(de.tum.cit.aet.artemis.math.domain.MathNode)} so that
 * numeric literals match student input regardless of incidental textual form
 * ({@code 0} vs {@code 0.0} vs {@code -0}).
 */
@Conditional(MathEnabled.class)
@Lazy
@Service
public class BlockRegistry {

    private final List<BlockDefinition> blocks;

    /** Indexed lookup populated at {@link #index()}. Replaces the previous linear scan. */
    private Map<String, RewriteRule> rulesById = Map.of();

    /** Per-block normalised rule lists; used by the registry endpoint so the wire form is canonical. */
    private Map<String, List<RewriteRule>> normalizedRulesByBlockType = Map.of();

    public BlockRegistry(List<BlockDefinition> blocks) {
        this.blocks = blocks;
    }

    /**
     * Builds the rule index and normalises each rule's literals. Invoked automatically by Spring
     * via {@code @PostConstruct}; exposed as {@code public} so tests can construct the registry
     * outside the container.
     */
    @PostConstruct
    public void index() {
        Map<String, RewriteRule> byId = new HashMap<>();
        Map<String, List<RewriteRule>> byBlockType = new HashMap<>();
        for (BlockDefinition block : blocks) {
            List<RewriteRule> normalized = block.getRules().stream().map(BlockRegistry::normalize).toList();
            byBlockType.put(block.getType(), normalized);
            for (RewriteRule rule : normalized) {
                if (byId.put(rule.id(), rule) != null) {
                    throw new IllegalStateException("Duplicate rule id across block definitions: " + rule.id());
                }
            }
        }
        this.rulesById = Map.copyOf(byId);
        this.normalizedRulesByBlockType = Map.copyOf(byBlockType);
    }

    private static RewriteRule normalize(RewriteRule rule) {
        return new RewriteRule(rule.id(), rule.name(), rule.paletteLatex(), MathNodes.normalize(rule.pattern()), MathNodes.normalize(rule.template()), rule.direction(),
                rule.constraints());
    }

    public List<BlockDefinition> getAllBlocks() {
        return blocks;
    }

    /**
     * Returns every block's normalised recursive definitions (the INDUCTION-mode trusted rules), across all
     * blocks. Contributed in code via {@link BlockDefinition#getDefinitions()}.
     *
     * @return the code-contributed definitions with canonical literals
     */
    public List<RewriteRule> getAllDefinitions() {
        return blocks.stream().flatMap(block -> block.getDefinitions().stream()).map(BlockRegistry::normalize).toList();
    }

    /**
     * Returns the normalised rule list for the given block (used by {@link #getAllBlocks()} consumers).
     *
     * @param block the block whose rules should be returned
     * @return the rules with canonical literals, or an empty list if the block is unknown
     */
    public List<RewriteRule> getNormalizedRulesFor(BlockDefinition block) {
        return normalizedRulesByBlockType.getOrDefault(block.getType(), List.of());
    }

    /**
     * Returns the normalised recursive definitions contributed by the given block (e.g. {@code pow_zero} /
     * {@code pow_succ} from the power block). Serialised alongside the rules so the induction workspace can
     * offer them in its palette.
     *
     * @param block the block whose definitions should be returned
     * @return the definitions with canonical literals, or an empty list if the block contributes none
     */
    public List<RewriteRule> getNormalizedDefinitionsFor(BlockDefinition block) {
        return block.getDefinitions().stream().map(BlockRegistry::normalize).toList();
    }

    public Optional<RewriteRule> findRuleById(String ruleId) {
        return Optional.ofNullable(rulesById.get(ruleId));
    }
}
