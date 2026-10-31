package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftAction;
import be.dontletit.draft.api.DraftCardView;
import be.dontletit.draft.api.DraftUpdate;
import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.OpenTableRequest;
import be.dontletit.draft.api.SeatTicket;
import be.dontletit.draft.domain.PickTimer;
import be.dontletit.draft.stubs.CardPoolSpiStub;
import be.dontletit.draft.stubs.InMemoryDraftRepository;
import be.dontletit.draft.stubs.ManualTimer;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/** One adapter on in-memory stubs; {@link #restart()} builds a fresh one on the same data. */
abstract class AdapterFixture {

    CardPoolSpiStub pool;
    InMemoryDraftRepository repository;
    ManualTimer timer;
    DraftApiAdapter adapter;

    @BeforeEach
    void setUpAdapter() {
        pool = new CardPoolSpiStub();
        repository = new InMemoryDraftRepository();
        timer = new ManualTimer();
        adapter = newAdapter();
    }

    DraftApiAdapter newAdapter() {
        return new DraftApiAdapter(pool, repository, timer, PickTimer.DEFAULT, () -> new Random(1L));
    }

    /** What a service restart does: a new adapter that only knows what the repository kept. */
    void restart() {
        adapter.shutdown();
        adapter = newAdapter();
        adapter.resumeRunningDrafts();
    }

    SeatTicket solo(String format) {
        return adapter.openTable(new OpenTableRequest("random", null, format, "Amir", false, true));
    }

    SeatTicket timedSolo(String format) {
        return adapter.openTable(new OpenTableRequest("random", null, format, "Amir", true, true));
    }

    SeatTicket lobby(String format, boolean timed) {
        return adapter.openTable(new OpenTableRequest("random", null, format, "Host", timed, false));
    }

    DraftView pickFirst(SeatTicket ticket) {
        DraftView view = adapter.view(ticket.draftId(), ticket.token());
        return adapter.act(ticket.draftId(), ticket.token(), DraftAction.pick(view.pack().get(0).name()));
    }

    static List<String> names(List<DraftCardView> cards) {
        return cards.stream().map(DraftCardView::name).toList();
    }

    /** Collects the updates a seat receives. */
    static final class Recorder {
        final List<DraftUpdate> updates = new CopyOnWriteArrayList<>();

        void onUpdate(DraftUpdate update) {
            updates.add(update);
        }

        DraftUpdate last() {
            return updates.get(updates.size() - 1);
        }
    }
}
