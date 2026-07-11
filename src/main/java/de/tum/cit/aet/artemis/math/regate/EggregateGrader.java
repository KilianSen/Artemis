package de.tum.cit.aet.artemis.math.regate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/** Regate eggregate backend (egglog equality saturation + proof-producing e-graph). */
@Lazy
@Service
@Conditional(MathEnabled.class)
public class EggregateGrader extends AbstractRegateGrader {

    @Value("${artemis.regate.eggregate.url:}")
    private String url;

    public EggregateGrader(RegateClient client, BlockRegistry blockRegistry) {
        super(client, blockRegistry);
    }

    @Override
    public GraderType getType() {
        return GraderType.EGGREGATE;
    }

    @Override
    protected String backendUrl() {
        return url;
    }
}
