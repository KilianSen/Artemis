package de.tum.cit.aet.artemis.math.regate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest;
import de.tum.cit.aet.artemis.math.regate.dto.GradeResponse;

/**
 * HTTP client for the Regate grading protocol: POSTs a {@link GradeRequest} to a backend's {@code /grade}
 * endpoint and returns the {@link GradeResponse}.
 * <p>
 * Uses the JDK {@link HttpClient} directly (not Spring's {@code RestClient}) with a pre-serialized string body
 * so the request carries a definite {@code Content-Length}. This matters because the Regate backends read the
 * body by {@code Content-Length} (a minimal {@code http.server}); Spring's {@code RestClient} streams the body
 * with {@code Transfer-Encoding: chunked} (no length known up front), which those backends see as an empty
 * body and reject with "exercise.source is required". HTTP/1.1 is forced to avoid an h2c upgrade attempt.
 * <p>
 * Serialization uses the shared, default-naming {@link JsonObjectMapper} so the DTOs' explicit
 * {@code @JsonProperty} wire names are honoured exactly, independent of the app's MVC Jackson config.
 */
@Lazy
@Service
@Conditional(MathEnabled.class)
public class RegateClient {

    private static final Logger log = LoggerFactory.getLogger(RegateClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();

    /** Default per-request read timeout, used when a caller does not specify a per-lane deadline. */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    /**
     * Grades a request against a backend with the {@link #DEFAULT_TIMEOUT default timeout}.
     *
     * @param backendUrl base URL of the backend (e.g. {@code http://regate-lean:8001})
     * @param request    the grade request (MathNodes already in protocol vocabulary)
     * @return the backend's grade response
     * @throws RegateException on transport failure, a non-2xx response, or an empty body
     */
    public GradeResponse grade(String backendUrl, GradeRequest request) {
        return grade(backendUrl, request, DEFAULT_TIMEOUT);
    }

    /**
     * Grades a request against a backend with an explicit read timeout — the per-lane deadline (fast graders get a
     * short timeout, slow formal certifiers a long one) so a hung backend fails fast instead of riding the executor.
     *
     * @param backendUrl base URL of the backend (e.g. {@code http://regate-lean:8001})
     * @param request    the grade request (MathNodes already in protocol vocabulary)
     * @param timeout    the per-request read timeout (a timeout throws {@link RegateException}, routed to retry/review)
     * @return the backend's grade response
     * @throws RegateException on transport failure, a non-2xx response, or an empty body
     */
    public GradeResponse grade(String backendUrl, GradeRequest request, Duration timeout) {
        String url = backendUrl + "/grade";
        try {
            String json = JsonObjectMapper.get().writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json").timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                throw new RegateException("Regate backend call to " + url + " returned HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
            }
            GradeResponse response = JsonObjectMapper.get().readValue(httpResponse.body(), GradeResponse.class);
            if (response == null) {
                throw new RegateException("Regate backend returned an empty body from " + url);
            }
            return response;
        }
        catch (IOException e) {
            log.warn("Regate grade call to {} failed: {}", url, e.getMessage());
            throw new RegateException("Regate backend call to " + url + " failed", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RegateException("Regate backend call to " + url + " was interrupted", e);
        }
    }
}
