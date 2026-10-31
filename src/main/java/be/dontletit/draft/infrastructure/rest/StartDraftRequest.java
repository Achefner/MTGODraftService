package be.dontletit.draft.infrastructure.rest;

/**
 * `{"source": "random"}` or `{"source": "cube", "collectionId": "..."}`, with an optional
 * `"format"`: CLASSIC (default), ROCHESTER or WINSTON.
 */
public record StartDraftRequest(String source, String collectionId, String format) {}
