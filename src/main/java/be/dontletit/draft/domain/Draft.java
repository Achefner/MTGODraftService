package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.DraftCommand;
import be.dontletit.draft.domain.event.BoosterDraftStarted;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.domain.event.WinstonDraftStarted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A draft in progress, whatever its format. It is event sourced: {@link #handle} checks a command
 * and records the events it causes; {@link #replay} rebuilds the same draft from those events.
 * Seats are numbered from 0; the draft does not know which seats are people and which are bots.
 */
public abstract sealed class Draft permits ClassicDraft, RochesterDraft, WinstonDraft {

    private final List<DraftEvent> history = new ArrayList<>();
    private final List<DraftEvent> historyView = Collections.unmodifiableList(history);

    /** Shuffles the pool and starts a draft of the given format. Extra cards are left out. */
    public static Draft start(DraftFormat format, String id, List<DraftCard> cards, Random random) {
        int size = cards == null ? 0 : cards.size();
        if (size < format.cardsNeeded()) {
            throw new IllegalArgumentException("A " + format.displayName() + " draft needs " + format.cardsNeeded() + " cards, got " + size);
        }
        List<DraftCard> shuffled = new ArrayList<>(cards);
        Collections.shuffle(shuffled, random);
        List<DraftCard> used = shuffled.subList(0, format.cardsNeeded());
        return switch (format) {
            case CLASSIC -> ClassicDraft.start(id, used);
            case ROCHESTER -> RochesterDraft.start(id, used);
            case WINSTON -> WinstonDraft.start(id, used, random.nextInt(format.seats()));
        };
    }

    /** Rebuilds a draft from its history. */
    public static Draft replay(List<DraftEvent> events) {
        if (events == null || events.isEmpty()) throw new IllegalArgumentException("A draft history starts with a start event");
        Draft draft = switch (events.get(0)) {
            case BoosterDraftStarted started when started.format() == DraftFormat.CLASSIC -> new ClassicDraft();
            case BoosterDraftStarted started when started.format() == DraftFormat.ROCHESTER -> new RochesterDraft();
            case WinstonDraftStarted ignored -> new WinstonDraft();
            default -> throw new IllegalArgumentException("A draft history starts with a start event, got " + events.get(0));
        };
        events.forEach(draft::record);
        return draft;
    }

    public abstract String id();

    public abstract DraftFormat format();

    public abstract boolean finished();

    /** Whether the seat has something to do right now. */
    public abstract boolean canAct(int seat);

    /** Every command the seat may send right now; empty when it cannot act. */
    public abstract List<DraftCommand> legalCommands(int seat);

    /** The cards a seat has drafted, in order. */
    public abstract List<DraftCard> picksOf(int seat);

    /** Everything that happened, oldest first. */
    public final List<DraftEvent> history() {
        return historyView;
    }

    /**
     * Executes a seat's command.
     * @return the events it caused, in order
     * @throws IllegalArgumentException when the seat does not exist or the command does not fit the format or the state
     * @throws IllegalStateException when the draft is over or it is not the seat's turn
     */
    public final List<DraftEvent> handle(DraftCommand command) {
        if (command == null) throw new IllegalArgumentException("A command is required");
        int seat = command.seat();
        if (seat < 0 || seat >= format().seats()) throw new IllegalArgumentException("No seat " + seat + " in a " + format().displayName() + " draft");
        if (finished()) throw new IllegalStateException("The draft is over");
        if (!canAct(seat)) throw new IllegalStateException("It is not seat " + seat + "'s turn");
        int from = history.size();
        execute(command);
        return List.copyOf(history.subList(from, history.size()));
    }

    /** Checks the command against the state and {@link #record}s its events. */
    protected abstract void execute(DraftCommand command);

    /** Changes the state for one event; must be deterministic so that replay works. */
    protected abstract void apply(DraftEvent event);

    protected final void record(DraftEvent event) {
        apply(event);
        history.add(event);
    }

    protected static DraftCard findCard(List<DraftCard> cards, String cardName, String where) {
        if (cardName != null) {
            for (DraftCard card : cards) {
                if (card.name().equalsIgnoreCase(cardName.trim())) return card;
            }
        }
        throw new IllegalArgumentException("Card is not " + where + ": " + cardName);
    }

    protected static List<List<DraftCard>> boosters(List<DraftCard> cards, int count, int size) {
        List<List<DraftCard>> boosters = new ArrayList<>();
        for (int i = 0; i < count; i++) boosters.add(List.copyOf(cards.subList(i * size, (i + 1) * size)));
        return boosters;
    }

}
