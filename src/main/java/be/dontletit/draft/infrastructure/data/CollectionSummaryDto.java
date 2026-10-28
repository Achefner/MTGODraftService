package be.dontletit.draft.infrastructure.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionSummaryDto(String id, String name, String type, int cardCount) {}
