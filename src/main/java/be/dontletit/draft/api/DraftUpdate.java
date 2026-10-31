package be.dontletit.draft.api;

import java.util.List;

/**
 * Pushed to a seat after every change. {@code version} counts the draft's events; {@code events}
 * are the ones the seat may see since its previous update; {@code view} is the seat's whole view.
 */
public record DraftUpdate(long version, List<DraftEventView> events, DraftView view) {}
