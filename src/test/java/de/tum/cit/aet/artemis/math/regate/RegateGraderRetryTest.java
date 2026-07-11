package de.tum.cit.aet.artemis.math.regate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest;
import de.tum.cit.aet.artemis.math.regate.dto.GradeResponse;
import de.tum.cit.aet.artemis.math.regate.dto.Outcome;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/**
 * The remote grade call retries a bounded number of times on a transient backend failure before propagating the
 * {@link RegateException} (which the caller then routes to review), and it applies the lane's read timeout.
 */
class RegateGraderRetryTest {

    private final RegateClient client = mock(RegateClient.class);

    private final BlockRegistry registry = mock(BlockRegistry.class);

    private static final GradeResponse OK = new GradeResponse("1.0", "eggregate", "0.1.0", Outcome.PROVEN_EQUAL, 100, true, null, null, null, null, "ok", null);

    /** A minimal fast (EGGREGATE) grader over the mocked client. */
    private AbstractRegateGrader grader() {
        return new AbstractRegateGrader(client, registry) {

            @Override
            protected String backendUrl() {
                return "http://localhost:8000";
            }

            @Override
            public GraderType getType() {
                return GraderType.EGGREGATE;
            }
        };
    }

    @Test
    void retriesOnceThenSucceeds() {
        when(client.grade(anyString(), any(GradeRequest.class), any(Duration.class))).thenThrow(new RegateException("transient")).thenReturn(OK);

        var result = grader().grade(new MathProblem(), List.of());

        assertThat(result.conclusive()).isTrue();
        assertThat(result.score()).isEqualTo(100);
        verify(client, times(2)).grade(anyString(), any(GradeRequest.class), any(Duration.class));
    }

    @Test
    void propagatesAfterExhaustingRetries() {
        when(client.grade(anyString(), any(GradeRequest.class), any(Duration.class))).thenThrow(new RegateException("backend down"));

        AbstractRegateGrader grader = grader();
        assertThatThrownBy(() -> grader.grade(new MathProblem(), List.of())).isInstanceOf(RegateException.class);
        // exactly the bounded number of attempts, not an unbounded loop
        verify(client, times(2)).grade(anyString(), any(GradeRequest.class), any(Duration.class));
    }

    @Test
    void fastLaneUsesShortTimeout() {
        when(client.grade(anyString(), any(GradeRequest.class), any(Duration.class))).thenReturn(OK);

        grader().grade(new MathProblem(), List.of());

        // EGGREGATE is a fast-lane grader, so it must not use the multi-minute slow deadline.
        var timeoutCaptor = org.mockito.ArgumentCaptor.forClass(Duration.class);
        verify(client).grade(anyString(), any(GradeRequest.class), timeoutCaptor.capture());
        assertThat(timeoutCaptor.getValue()).isLessThanOrEqualTo(Duration.ofSeconds(30));
    }
}
