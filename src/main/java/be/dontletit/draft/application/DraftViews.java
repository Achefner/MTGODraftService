package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftCardView;
import be.dontletit.draft.api.DraftEventView;
import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.SeatView;
import be.dontletit.draft.api.TablePreview;
import be.dontletit.draft.api.TimerView;
import be.dontletit.draft.api.WinstonView;
import be.dontletit.draft.domain.ClassicDraft;
import be.dontletit.draft.domain.Draft;
import be.dontletit.draft.domain.RochesterDraft;
import be.dontletit.draft.domain.WinstonDraft;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.spi.PlayerRecord;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** What a seat sees of a table. */
final class DraftViews {

    /** How many of the latest visible events a view carries. */
    static final int LOG_SIZE = 30;

    private DraftViews() {}

    static DraftView view(DraftSession session, int seat, java.time.Instant now) {
        Draft draft = session.draft;
        String id = session.id();
        String source = session.table.source();
        String format = session.table.format().name();
        String status = session.table.status().name();
        boolean host = seat == 0;
        long version = session.version();
        List<SeatView> seats = seats(session, seat);
        if (draft == null) {
            return new DraftView(id, source, format, status, version, seat, host, false, false, 0, null,
                    null, null, null, null, null, null, List.of(), List.of(), null, seats, List.of());
        }
        boolean yourTurn = draft.canAct(seat);
        int moves = draft.moveCount(seat);
        DraftSession.SeatClock clock = session.clocks.get(seat);
        TimerView timer = clock == null ? null
                : new TimerView(clock.deadline(), clock.seconds(), Math.max(0, java.time.Duration.between(now, clock.deadline()).toMillis()));
        List<DraftCardView> picks = EventProjection.cards(draft.picksOf(seat));
        List<DraftEventView> log = log(draft.history(), seat);
        return switch (draft) {
            case ClassicDraft classic -> new DraftView(id, source, format, status, version, seat, host, yourTurn, classic.finished(), moves, timer,
                    classic.round(), classic.pickNumber(seat), classic.direction().name(), null, null, null,
                    EventProjection.cards(classic.packFor(seat)), picks, null, seats, log);
            case RochesterDraft rochester -> new DraftView(id, source, format, status, version, seat, host, yourTurn, rochester.finished(), moves, timer,
                    rochester.round(), rochester.pickInPack(), rochester.direction().name(),
                    rochester.packNumber(), rochester.opener(), rochester.activeSeat(),
                    EventProjection.cards(rochester.table()), picks, null, seats, log);
            case WinstonDraft winston -> new DraftView(id, source, format, status, version, seat, host, yourTurn, winston.finished(), moves, timer,
                    null, null, null, null, null, winston.activeSeat(),
                    yourTurn ? EventProjection.cards(winston.pile(winston.currentPile())) : List.of(), picks,
                    new WinstonView(winston.pileSizes(), winston.currentPile(), winston.stackSize()), seats, log);
        };
    }

    static TablePreview preview(DraftSession session) {
        return new TablePreview(session.id(), session.table.format().name(), session.table.source(),
                session.table.status().name(), session.table.timed(), seats(session, -1));
    }

    private static List<SeatView> seats(DraftSession session, int viewer) {
        return IntStream.range(0, session.table.format().seats()).mapToObj(seat -> {
            Optional<PlayerRecord> player = session.player(seat);
            String kind = player.isPresent() ? "HUMAN" : session.draft == null ? "OPEN" : "BOT";
            int picks = session.draft == null ? 0 : session.draft.picksOf(seat).size();
            return new SeatView(seat, seat == viewer, player.map(PlayerRecord::name).orElse(null), kind,
                    player.isPresent() && session.connected(seat), picks);
        }).toList();
    }

    /** The events after {@code from} that the seat may see. */
    static List<DraftEventView> visibleSince(List<DraftEvent> history, long from, int seat) {
        return history.subList((int) Math.min(from, history.size()), history.size()).stream()
                .map(event -> EventProjection.forSeat(event, seat))
                .flatMap(Optional::stream)
                .toList();
    }

    private static List<DraftEventView> log(List<DraftEvent> history, int seat) {
        List<DraftEventView> visible = visibleSince(history, 0, seat);
        return visible.subList(Math.max(0, visible.size() - LOG_SIZE), visible.size());
    }
}
