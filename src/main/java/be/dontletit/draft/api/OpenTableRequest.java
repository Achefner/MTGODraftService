package be.dontletit.draft.api;

/**
 * {@code source} is "random" or "cube" (then {@code collectionId}); {@code format} defaults to
 * CLASSIC; {@code timed} turns the pick timer on; {@code startNow} starts at once against bots.
 */
public record OpenTableRequest(String source, String collectionId, String format, String playerName, boolean timed, boolean startNow) {}
