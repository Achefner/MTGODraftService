package be.dontletit.draft.infrastructure.rest;

/** `{"source": "random"}` or `{"source": "cube", "collectionId": "..."}`. */
public record StartDraftRequest(String source, String collectionId) {}
