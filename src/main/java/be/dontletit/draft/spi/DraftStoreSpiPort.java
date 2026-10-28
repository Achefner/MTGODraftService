package be.dontletit.draft.spi;

import be.dontletit.draft.domain.Draft;

import java.util.Optional;

/** Outbound port holding drafts in progress. */
public interface DraftStoreSpiPort {
    void save(Draft draft);
    Optional<Draft> find(String draftId);
}
