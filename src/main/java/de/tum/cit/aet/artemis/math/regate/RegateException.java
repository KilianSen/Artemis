package de.tum.cit.aet.artemis.math.regate;

/**
 * Thrown when a Regate backend call cannot produce a grade — a transport failure, a non-2xx response, an
 * empty body, or an unconfigured backend. The async grading layer (Phase 2) catches this to route the
 * submission to review rather than failing the submit.
 */
public class RegateException extends RuntimeException {

    /** HTTP status of the offending response, or {@code 0} when the failure was not an HTTP response (transport, empty body, unconfigured). */
    private final int statusCode;

    public RegateException(String message) {
        this(message, 0);
    }

    public RegateException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public RegateException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    /**
     * @return the HTTP status of the backend response that caused this, or {@code 0} for a non-HTTP failure. A
     *         {@code 4xx} is a deterministic client error (e.g. protocol/vocabulary the backend does not implement,
     *         per {@code GRADING_PROTOCOL.md} "Unimplemented vocabulary"): retrying it cannot help.
     */
    public int getStatusCode() {
        return statusCode;
    }
}
