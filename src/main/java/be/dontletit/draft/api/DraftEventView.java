package be.dontletit.draft.api;

import java.util.List;

/**
 * Something that happened in the draft, as one seat is allowed to see it.
 * {@code type} is one of DRAFT_STARTED, ROUND_STARTED, PACK_OPENED, CARD_PICKED, PILE_TAKEN,
 * PILE_PASSED, STACK_CARD_TAKEN, DRAFT_FINISHED. Fields that do not apply are null;
 * {@code cards} only holds cards the seat may see. {@code pile} counts from 1.
 */
public record DraftEventView(
        String type,
        Integer seat,
        Integer round,
        String direction,
        Integer packNumber,
        Integer pile,
        Integer cardCount,
        List<DraftCardView> cards
) {}
