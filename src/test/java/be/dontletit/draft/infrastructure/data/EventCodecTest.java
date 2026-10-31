package be.dontletit.draft.infrastructure.data;

import be.dontletit.draft.domain.DraftCard;
import be.dontletit.draft.domain.DraftFormat;
import be.dontletit.draft.domain.PassDirection;
import be.dontletit.draft.domain.event.BoosterDraftStarted;
import be.dontletit.draft.domain.event.CardPicked;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.domain.event.DraftFinished;
import be.dontletit.draft.domain.event.PackOpened;
import be.dontletit.draft.domain.event.PackPassed;
import be.dontletit.draft.domain.event.PilePassed;
import be.dontletit.draft.domain.event.PileTaken;
import be.dontletit.draft.domain.event.RoundStarted;
import be.dontletit.draft.domain.event.StackCardTaken;
import be.dontletit.draft.domain.event.WinstonDraftStarted;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventCodecTest {

    static final DraftCard BOLT = new DraftCard("Lightning Bolt", "https://img/bolt.jpg");
    static final DraftCard OKO = new DraftCard("Oko, Thief of Crowns", null);

    final EventCodec codec = new EventCodec(new ObjectMapper());

    static final List<DraftEvent> EVERY_KIND = List.of(
            new BoosterDraftStarted("d1", DraftFormat.ROCHESTER, List.of(List.of(BOLT, OKO), List.of(OKO))),
            new WinstonDraftStarted("d2", List.of(BOLT, OKO), 1),
            new RoundStarted(2, PassDirection.RIGHT),
            new PackOpened(1, 3, 2, List.of(BOLT)),
            new CardPicked(4, BOLT, true),
            new PackPassed(0, 7, 13),
            new PileTaken(1, 2, List.of(BOLT, OKO)),
            new PilePassed(0, 1),
            new StackCardTaken(1, OKO),
            new DraftFinished());

    @Test
    void everyKindOfEvent_survivesTheRoundTrip() {
        assertEquals(DraftEvent.class.getPermittedSubclasses().length, EVERY_KIND.size(), "a new event kind needs a case here");
        for (DraftEvent event : EVERY_KIND) {
            EventCodec.Encoded encoded = codec.encode(event);
            assertEquals(event.getClass().getSimpleName(), encoded.type());
            assertEquals(event, codec.decode(encoded.type(), encoded.payload()), encoded.payload());
        }
    }

    @Test
    void theTypeNamesEveryPermittedEvent() {
        Arrays.stream(DraftEvent.class.getPermittedSubclasses())
                .forEach(type -> assertTrue(EVERY_KIND.stream().anyMatch(e -> e.getClass() == type), type.getSimpleName()));
    }

    @Test
    void unknownType_orBrokenPayload_fail() {
        assertThrows(IllegalStateException.class, () -> codec.decode("Shuffled", "{}"));
        assertThrows(IllegalStateException.class, () -> codec.decode("CardPicked", "not json"));
    }
}
