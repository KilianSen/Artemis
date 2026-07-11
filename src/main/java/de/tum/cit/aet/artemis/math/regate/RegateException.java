package de.tum.cit.aet.artemis.math.regate;

/**
 * Thrown when a Regate backend call cannot produce a grade — a transport failure, a non-2xx response, an
 * empty body, or an unconfigured backend. The async grading layer (Phase 2) catches this to route the
 * submission to review rather than failing the submit.
 */
public class RegateException extends RuntimeException {

    public RegateException(String message) {
        super(message);
    }

    public RegateException(String message, Throwable cause) {
        super(message, cause);
    }
}
