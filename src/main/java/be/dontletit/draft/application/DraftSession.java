package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftListener;
import be.dontletit.draft.domain.Draft;
import be.dontletit.draft.domain.RandomBot;
import be.dontletit.draft.spi.PlayerRecord;
import be.dontletit.draft.spi.TableRecord;
import be.dontletit.draft.spi.TableStatus;
import be.dontletit.draft.spi.TimerSpiPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A table held in memory: its record, its draft once started, who is listening and the running
 * pick clocks. Every use goes through {@code synchronized (session)}.
 */
final class DraftSession {

    /** A seat's clock for one decision: it belongs to the move count it was armed for. */
    record SeatClock(int moves, Instant deadline, int seconds, TimerSpiPort.Cancellable alarm) {}

    /** One open connection of a seat, and the last version it was sent. */
    static final class Listener {
        final int seat;
        final DraftListener target;
        long lastSent;

        Listener(int seat, DraftListener target, long lastSent) {
            this.seat = seat;
            this.target = target;
            this.lastSent = lastSent;
        }
    }

    TableRecord table;
    Draft draft;
    final RandomBot bot;
    final List<Listener> listeners = new ArrayList<>();
    final Map<Integer, SeatClock> clocks = new HashMap<>();

    DraftSession(TableRecord table, Draft draft, RandomBot bot) {
        this.table = table;
        this.draft = draft;
        this.bot = bot;
    }

    String id() {
        return table.id();
    }

    boolean running() {
        return table.status() == TableStatus.RUNNING && draft != null && !draft.finished();
    }

    long version() {
        return draft == null ? 0 : draft.history().size();
    }

    Set<Integer> humanSeats() {
        return table.players().stream().map(PlayerRecord::seat).collect(Collectors.toSet());
    }

    Optional<PlayerRecord> player(int seat) {
        return table.players().stream().filter(p -> p.seat() == seat).findFirst();
    }

    boolean connected(int seat) {
        return listeners.stream().anyMatch(l -> l.seat == seat);
    }

    void cancelClocks() {
        clocks.values().forEach(clock -> clock.alarm().cancel());
        clocks.clear();
    }
}
