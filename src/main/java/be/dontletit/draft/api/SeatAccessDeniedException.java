package be.dontletit.draft.api;

/** The seat token is missing, unknown for this draft, or lacks the right (only the host starts). */
public class SeatAccessDeniedException extends RuntimeException {
    public SeatAccessDeniedException(String message) {
        super(message);
    }
}
