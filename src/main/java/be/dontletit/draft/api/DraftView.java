package be.dontletit.draft.api;

import java.util.List;

/** Everything the client needs to render the human player's seat. */
public record DraftView(
        String id,
        String source,
        int round,
        int pickNumber,
        String direction,
        boolean finished,
        List<DraftCardView> pack,
        List<DraftCardView> picks
) {}
