package be.dontletit.draft.spi;

import be.dontletit.draft.domain.event.DraftEvent;

import java.util.List;
import java.util.Optional;

/** Where tables and draft histories live, so that drafts survive a restart. */
public interface DraftRepositorySpiPort {

    /** Inserts or replaces the table and its players. */
    void saveTable(TableRecord table);

    Optional<TableRecord> findTable(String draftId);

    List<TableRecord> findTables(TableStatus status);

    /**
     * Appends events whose positions in the history start at {@code firstPosition}.
     * @throws IllegalStateException when a position is already taken (someone else wrote first)
     */
    void appendEvents(String draftId, int firstPosition, List<DraftEvent> events);

    /** The whole history, oldest first; empty for a table still in its lobby. */
    List<DraftEvent> loadEvents(String draftId);
}
