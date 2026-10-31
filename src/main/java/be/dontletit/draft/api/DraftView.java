package be.dontletit.draft.api;

import java.util.List;

/**
 * Everything the client needs to render one seat. {@code status} is LOBBY, RUNNING or FINISHED.
 * {@code version} counts the draft's events: a client keeps the view with the highest version.
 * Fields that do not apply are null: round, pickNumber and direction for booster formats (Classic,
 * Rochester); packNumber and opener for Rochester; activeSeat for Rochester and Winston; winston for
 * Winston; timer when the seat has no running clock. {@code moves} is what an action sends back as
 * {@code expectedMoves}. {@code pack} is the Classic pack in front of the seat, the Rochester table,
 * or the Winston pile the seat is looking at.
 */
public record DraftView(
        String id,
        String source,
        String format,
        String status,
        long version,
        int seat,
        boolean host,
        boolean yourTurn,
        boolean finished,
        int moves,
        TimerView timer,
        Integer round,
        Integer pickNumber,
        String direction,
        Integer packNumber,
        Integer opener,
        Integer activeSeat,
        List<DraftCardView> pack,
        List<DraftCardView> picks,
        WinstonView winston,
        List<SeatView> seats,
        List<DraftEventView> log
) {}
