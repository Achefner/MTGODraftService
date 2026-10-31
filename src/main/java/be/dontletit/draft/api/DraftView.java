package be.dontletit.draft.api;

import java.util.List;

/**
 * Everything the client needs to render the player's seat. Fields that do not apply to the
 * format are null: round, pickNumber and direction for booster formats (Classic, Rochester);
 * packNumber, opener and activeSeat for Rochester; winston for Winston.
 * {@code pack} is the Classic pack in front of the player, the Rochester table, or the Winston
 * pile the player is looking at.
 */
public record DraftView(
        String id,
        String source,
        String format,
        int seat,
        boolean yourTurn,
        boolean finished,
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
