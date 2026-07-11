package de.tum.cit.aet.artemis.math.regate;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathProblemConfig;
import de.tum.cit.aet.artemis.math.grader.GradingResult;
import de.tum.cit.aet.artemis.math.grader.GradingSpeed;
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

    private static final Logger log = LoggerFactory.getLogger(AbstractRegateGrader.class);

    /** Per-lane read timeout: fast graders answer in (sub-)seconds, so a hung fast backend fails fast. */
    private static final Duration FAST_TIMEOUT = Duration.ofSeconds(30);

    /** Slow formal certifiers (Lean/Coq) run multi-second-to-minute proofs, so they get a long deadline. */
    private static final Duration SLOW_TIMEOUT = Duration.ofMinutes(5);

    /** Total attempts (one bounded retry) before a transient backend failure routes the submission to review. */
    private static final int MAX_ATTEMPTS = 2;

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
        GradeResponse response = gradeWithRetry(request);
        return RegateResponseMapper.toGradingResult(response);
    }

    /**
     * POSTs the grade request with the lane's timeout, retrying a bounded number of times on a transient backend
     * failure. If every attempt fails the {@link RegateException} propagates — the caller then routes the submission
     * to manual review rather than recording a misleading zero.
     */
    private GradeResponse gradeWithRetry(GradeRequest request) {
        Duration timeout = getType().getSpeed() == GradingSpeed.SLOW ? SLOW_TIMEOUT : FAST_TIMEOUT;
        String url = requireUrl();
        RegateException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return client.grade(url, request, timeout);
            }
            catch (RegateException exception) {
                lastFailure = exception;
                log.warn("Regate grade attempt {}/{} on {} failed: {}", attempt, MAX_ATTEMPTS, getType(), exception.getMessage());
            }
        }
        throw lastFailure;
    }

    @Override
    public List<HintSuggestion> suggestHints(MathProblemConfig config, MathNode currentState) {
        GradeRequest request = RegateRequestMapper.toHintRequest(config, currentState, blockRegistry);
        if (request == null) {
            return List.of();
        }
        try {
            // Hints must be responsive, so always use the fast deadline regardless of the grader's lane.
            return RegateResponseMapper.toHints(client.grade(requireUrl(), request, FAST_TIMEOUT));
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
