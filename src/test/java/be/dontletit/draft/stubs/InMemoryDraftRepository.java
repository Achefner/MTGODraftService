package be.dontletit.draft.stubs;

import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.spi.DraftRepositorySpiPort;
import be.dontletit.draft.spi.TableRecord;
import be.dontletit.draft.spi.TableStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Keeps what a database would, so tests can "restart" the service on the same data. */
public class InMemoryDraftRepository implements DraftRepositorySpiPort {

    public final Map<String, TableRecord> tables = new HashMap<>();
    public final Map<String, List<DraftEvent>> events = new HashMap<>();
    public boolean failAppends;

    @Override
    public synchronized void saveTable(TableRecord table) {
        tables.put(table.id(), table);
    }

    @Override
    public synchronized Optional<TableRecord> findTable(String draftId) {
        return Optional.ofNullable(tables.get(draftId));
    }

    @Override
    public synchronized List<TableRecord> findTables(TableStatus status) {
        return tables.values().stream().filter(t -> t.status() == status).toList();
    }

    @Override
    public synchronized void appendEvents(String draftId, int firstPosition, List<DraftEvent> newEvents) {
        if (failAppends) throw new IllegalStateException("database down");
        List<DraftEvent> history = events.computeIfAbsent(draftId, id -> new ArrayList<>());
        if (history.size() != firstPosition) throw new IllegalStateException("position " + firstPosition + " is taken");
        history.addAll(newEvents);
    }

    @Override
    public synchronized List<DraftEvent> loadEvents(String draftId) {
        return List.copyOf(events.getOrDefault(draftId, List.of()));
    }
}
