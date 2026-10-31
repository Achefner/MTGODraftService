package be.dontletit.draft.infrastructure.data;

import be.dontletit.draft.domain.DraftCard;
import be.dontletit.draft.domain.DraftFormat;
import be.dontletit.draft.domain.PassDirection;
import be.dontletit.draft.domain.event.CardPicked;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.domain.event.RoundStarted;
import be.dontletit.draft.domain.event.WinstonDraftStarted;
import be.dontletit.draft.spi.PlayerRecord;
import be.dontletit.draft.spi.TableRecord;
import be.dontletit.draft.spi.TableStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JpaDraftRepositoryTest {

    @Inject
    JpaDraftRepository repository;

    static TableRecord table(TableStatus status) {
        return new TableRecord(UUID.randomUUID().toString(), DraftFormat.WINSTON, "cube:c1", status, true,
                Instant.parse("2026-11-01T10:00:00.123456Z"), List.of(new PlayerRecord(0, "Host", "hash-0")));
    }

    @Test
    void savesAndFindsATableWithItsPlayers() {
        TableRecord table = table(TableStatus.LOBBY);

        repository.saveTable(table);

        TableRecord found = repository.findTable(table.id()).orElseThrow();
        assertEquals(table.id(), found.id());
        assertEquals(DraftFormat.WINSTON, found.format());
        assertEquals("cube:c1", found.source());
        assertEquals(TableStatus.LOBBY, found.status());
        assertTrue(found.timed());
        assertEquals(table.createdAt().truncatedTo(ChronoUnit.MILLIS), found.createdAt().truncatedTo(ChronoUnit.MILLIS));
        assertEquals(table.players(), found.players());
        assertTrue(repository.findTable("nope").isEmpty());
    }

    @Test
    void savingAgain_replacesTheStatusAndThePlayers() {
        TableRecord table = table(TableStatus.LOBBY);
        repository.saveTable(table);

        TableRecord changed = table.withPlayer(new PlayerRecord(1, "Guest", "hash-1")).withStatus(TableStatus.RUNNING);
        repository.saveTable(changed);

        TableRecord found = repository.findTable(table.id()).orElseThrow();
        assertEquals(TableStatus.RUNNING, found.status());
        assertEquals(List.of(new PlayerRecord(0, "Host", "hash-0"), new PlayerRecord(1, "Guest", "hash-1")), found.players());
        assertTrue(repository.findTables(TableStatus.RUNNING).stream().anyMatch(t -> t.id().equals(table.id())));
        assertTrue(repository.findTables(TableStatus.LOBBY).stream().noneMatch(t -> t.id().equals(table.id())));
    }

    @Test
    void appendsAndLoadsTheHistoryInOrder() {
        TableRecord table = table(TableStatus.RUNNING);
        repository.saveTable(table);
        DraftCard bolt = new DraftCard("Lightning Bolt", "https://img/bolt.jpg");
        List<DraftEvent> first = List.of(new WinstonDraftStarted(table.id(), List.of(bolt), 0), new RoundStarted(1, PassDirection.LEFT));
        List<DraftEvent> second = List.of(new CardPicked(0, bolt, false));

        repository.appendEvents(table.id(), 0, first);
        repository.appendEvents(table.id(), 2, second);

        assertEquals(List.of(first.get(0), first.get(1), second.get(0)), repository.loadEvents(table.id()));
        assertTrue(repository.loadEvents("nope").isEmpty());
    }

    @Test
    void appendingAtATakenPosition_isRefused() {
        TableRecord table = table(TableStatus.RUNNING);
        repository.saveTable(table);
        repository.appendEvents(table.id(), 0, List.of(new RoundStarted(1, PassDirection.LEFT)));

        assertThrows(IllegalStateException.class,
                () -> repository.appendEvents(table.id(), 0, List.of(new RoundStarted(2, PassDirection.RIGHT))));
        assertThrows(IllegalStateException.class,
                () -> repository.appendEvents(table.id(), 5, List.of(new RoundStarted(2, PassDirection.RIGHT))), "no gaps either");
        assertEquals(1, repository.loadEvents(table.id()).size());
    }
}
