package be.dontletit.draft.infrastructure.rest;

/**
 * Opens a table: `{"source": "random"}` or `{"source": "cube", "collectionId": "..."}`, plus
 * optional `format` (CLASSIC, ROCHESTER, WINSTON), `playerName`, `timed` (pick timer, default true)
 * and `startNow` (play at once against bots, default true).
 */
public record StartDraftRequest(String source, String collectionId, String format, String playerName, Boolean timed, Boolean startNow) {}
