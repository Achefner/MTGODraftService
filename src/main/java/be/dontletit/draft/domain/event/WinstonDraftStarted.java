package be.dontletit.draft.domain.event;

import be.dontletit.draft.domain.DraftCard;

import java.util.List;

/** A Winston draft started: the main stack, top card first, and the seat that plays first. */
public record WinstonDraftStarted(String draftId, List<DraftCard> stack, int firstSeat) implements DraftEvent {
    public WinstonDraftStarted {
        stack = List.copyOf(stack);
    }
}
