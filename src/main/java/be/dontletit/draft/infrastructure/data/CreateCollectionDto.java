package be.dontletit.draft.infrastructure.data;

import java.util.List;

/** Body of MTGODatabase's POST /collections. */
public record CreateCollectionDto(String name, String type, List<CollectionEntryDto> entries) {}
