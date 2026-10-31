package be.dontletit.draft.spi;

import be.dontletit.draft.domain.DraftFormat;

import java.time.Instant;
import java.util.List;

/** A table as stored: what to draft and who sits there. Seats without a player are bots once it runs. */
public record TableRecord(String id, DraftFormat format, String source, TableStatus status, boolean timed,
                          Instant createdAt, List<PlayerRecord> players) {
    public TableRecord {
        players = List.copyOf(players);
    }

    public TableRecord withStatus(TableStatus newStatus) {
        return new TableRecord(id, format, source, newStatus, timed, createdAt, players);
    }

    public TableRecord withPlayer(PlayerRecord player) {
        List<PlayerRecord> more = new java.util.ArrayList<>(players);
        more.add(player);
        return new TableRecord(id, format, source, status, timed, createdAt, more);
    }
}
