package de.tum.cit.aet.artemis.math.regate;

import java.util.List;

import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathProblemConfig;
import de.tum.cit.aet.artemis.math.grader.GradingResult;
import de.tum.cit.aet.artemis.math.grader.HintSuggestion;
import de.tum.cit.aet.artemis.math.grader.MathGrader;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest;
import de.tum.cit.aet.artemis.math.regate.dto.GradeResponse;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/**
 * Base class for the four Regate HTTP backends. Each concrete subclass binds a {@code GraderType} and a
 * configured backend URL; the grading flow (assemble request → POST → map response) is shared. All four
 * speak the same protocol, differing only in the URL they target and the modes they certify.
 */
public abstract class AbstractRegateGrader implements MathGrader {

    private final RegateClient client;

    private final BlockRegistry blockRegistry;

    protected AbstractRegateGrader(RegateClient client, BlockRegistry blockRegistry) {
        this.client = client;
        this.blockRegistry = blockRegistry;
    }

    /**
     * @return the configured base URL of this backend, or blank/{@code null} if it is not deployed
     */
    protected abstract String backendUrl();

    @Override
    public GradingResult grade(MathProblemConfig config, List<DerivationStep> steps) {
        GradeRequest request = RegateRequestMapper.toRequest(config, steps, blockRegistry);
        GradeResponse response = client.grade(requireUrl(), request);
        return RegateResponseMapper.toGradingResult(response);
    }

    @Override
    public List<HintSuggestion> suggestHints(MathProblemConfig config, MathNode currentState) {
        GradeRequest request = RegateRequestMapper.toHintRequest(config, currentState, blockRegistry);
        if (request == null) {
            return List.of();
        }
        try {
            return RegateResponseMapper.toHints(client.grade(requireUrl(), request));
        }
        catch (RegateException exception) {
            // A backend that is down or cannot produce a hint should not surface an error at the hint button.
            return List.of();
        }
    }

    private String requireUrl() {
        String url = backendUrl();
        if (url == null || url.isBlank()) {
            throw new RegateException(getType() + " backend is not configured (set artemis.regate.<backend>.url)");
        }
        return url;
    }
}
