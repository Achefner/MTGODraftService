package be.dontletit.draft.domain.event;

import be.dontletit.draft.domain.DraftCard;
import be.dontletit.draft.domain.DraftFormat;

import java.util.List;

/**
 * A Classic or Rochester draft started. {@code packs} holds every sealed booster:
 * pack {@code (round - 1) * seats + seat} belongs to {@code seat} for {@code round}.
 */
public record BoosterDraftStarted(String draftId, DraftFormat format, List<List<DraftCard>> packs) implements DraftEvent {
    public BoosterDraftStarted {
        packs = packs.stream().map(List::copyOf).toList();
    }
}
