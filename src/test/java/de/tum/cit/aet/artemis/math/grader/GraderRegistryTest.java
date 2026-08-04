package de.tum.cit.aet.artemis.math.grader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.math.domain.BlockDefinition;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathProblemConfig;
import de.tum.cit.aet.artemis.math.domain.blocks.AddBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.EqualityBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.FractionBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.MulBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.NumberBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.SubBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.VariableBlockDefinition;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;
import de.tum.cit.aet.artemis.math.service.RuleSubsetPolicy;

class GraderRegistryTest {

    private GraderRegistry registry;

    private PathCheckerGrader pathCheckerGrader;

    @BeforeEach
    void setUp() {
        List<BlockDefinition> blocks = List.of(new NumberBlockDefinition(), new VariableBlockDefinition(), new AddBlockDefinition(), new SubBlockDefinition(),
                new MulBlockDefinition(), new FractionBlockDefinition(), new EqualityBlockDefinition());
        BlockRegistry blockRegistry = new BlockRegistry(blocks);
        blockRegistry.index();
        pathCheckerGrader = new PathCheckerGrader(blockRegistry, new RuleSubsetPolicy(blockRegistry));
        registry = new GraderRegistry(List.of(pathCheckerGrader));
        registry.index();
    }

    @Test
    void getGrader_pathChecker_returnsTheBean() {
        assertThat(registry.getGrader(GraderType.PATH_CHECKER)).isSameAs(pathCheckerGrader);
    }

    @Test
    void getGrader_unregisteredType_throws() {
        assertThatThrownBy(() -> registry.getGrader(GraderType.LEANREGATE)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No MathGrader");
    }

    @Test
    void index_duplicateType_throws() {
        MathGrader duplicate = new MathGrader() {

            @Override
            public GraderType getType() {
                return GraderType.PATH_CHECKER;
            }

            @Override
            public GradingResult grade(MathProblemConfig config, List<DerivationStep> steps) {
                return GradingResult.of(0.0);
            }
        };
        GraderRegistry duped = new GraderRegistry(List.of(pathCheckerGrader, duplicate));
        assertThatThrownBy(duped::index).isInstanceOf(IllegalStateException.class).hasMessageContaining("Multiple MathGrader");
    }
}
