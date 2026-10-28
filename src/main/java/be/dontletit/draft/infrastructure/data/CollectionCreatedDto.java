package be.dontletit.draft.infrastructure.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionCreatedDto(String id) {}
