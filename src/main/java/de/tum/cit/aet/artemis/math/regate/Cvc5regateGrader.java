package de.tum.cit.aet.artemis.math.regate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/** Regate cvc5regate backend (cvc5 SMT with native structural induction); a general grader that also certifies induction. */
@Lazy
@Service
@Conditional(MathEnabled.class)
public class Cvc5regateGrader extends AbstractRegateGrader {

    @Value("${artemis.regate.cvc5regate.url:}")
    private String url;

    public Cvc5regateGrader(RegateClient client, BlockRegistry blockRegistry) {
        super(client, blockRegistry);
    }

    @Override
    public GraderType getType() {
        return GraderType.CVC5REGATE;
    }

    @Override
    protected String backendUrl() {
        return url;
    }
}
