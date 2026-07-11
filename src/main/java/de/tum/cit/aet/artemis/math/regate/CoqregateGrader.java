package de.tum.cit.aet.artemis.math.regate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/** Regate coqregate backend (Rocq/Coq); a specialist induction certifier. */
@Lazy
@Service
@Conditional(MathEnabled.class)
public class CoqregateGrader extends AbstractRegateGrader {

    @Value("${artemis.regate.coqregate.url:}")
    private String url;

    public CoqregateGrader(RegateClient client, BlockRegistry blockRegistry) {
        super(client, blockRegistry);
    }

    @Override
    public GraderType getType() {
        return GraderType.COQREGATE;
    }

    @Override
    protected String backendUrl() {
        return url;
    }
}
