package de.tum.cit.aet.artemis.math.regate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/** Regate leanregate backend (Lean formal proofs); the only backend that certifies induction. */
@Lazy
@Service
@Conditional(MathEnabled.class)
public class LeanregateGrader extends AbstractRegateGrader {

    @Value("${artemis.regate.leanregate.url:}")
    private String url;

    public LeanregateGrader(RegateClient client, BlockRegistry blockRegistry) {
        super(client, blockRegistry);
    }

    @Override
    public GraderType getType() {
        return GraderType.LEANREGATE;
    }

    @Override
    protected String backendUrl() {
        return url;
    }
}
