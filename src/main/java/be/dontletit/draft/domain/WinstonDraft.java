package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.DraftCommand;
import be.dontletit.draft.domain.command.PassPile;
import be.dontletit.draft.domain.command.TakePile;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.domain.event.DraftFinished;
import be.dontletit.draft.domain.event.PilePassed;
import be.dontletit.draft.domain.event.PileTaken;
import be.dontletit.draft.domain.event.StackCardTaken;
import be.dontletit.draft.domain.event.WinstonDraftStarted;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Winston draft for 2 seats: 90 cards form the main stack and the top three start three piles.
 * On a turn the seat looks at the first pile and takes it (the pile is refilled with one card from
 * the stack) or passes it (one card from the stack is added to it) and looks at the next pile.
 * Passing the third pile means taking the top card of the stack.
 * Once the stack is empty, passing adds nothing, empty piles are skipped, and a seat must take
 * the pile in front of it when passing would leave nothing to take.
 */
public final class WinstonDraft extends Draft {

    public static final int PILES = 3;
    private static final int SEATS = DraftFormat.WINSTON.seats();

    private String id;
    private final Deque<DraftCard> stack = new ArrayDeque<>();
    private final List<List<DraftCard>> piles = new ArrayList<>();
    private final List<List<DraftCard>> picks = new ArrayList<>();
    private int activeSeat;
    private int currentPile;
    private boolean finished;

    WinstonDraft() {
        for (int pile = 0; pile < PILES; pile++) piles.add(new ArrayList<>());
        for (int seat = 0; seat < SEATS; seat++) picks.add(new ArrayList<>());
    }

    static WinstonDraft start(String id, List<DraftCard> cards, int firstSeat) {
        WinstonDraft draft = new WinstonDraft();
        draft.record(new WinstonDraftStarted(id, cards, firstSeat));
        return draft;
    }

    @Override public String id() { return id; }
    @Override public DraftFormat format() { return DraftFormat.WINSTON; }
    @Override public boolean finished() { return finished; }
    public int activeSeat() { return activeSeat; }
    /** The pile the active seat is looking at, 0-based. */
    public int currentPile() { return currentPile; }
    public int stackSize() { return stack.size(); }

    public List<Integer> pileSizes() {
        return piles.stream().map(List::size).toList();
    }

    /** The cards of a pile; only the active seat should see the one it is looking at. */
    public List<DraftCard> pile(int pile) {
        return Collections.unmodifiableList(piles.get(pile));
    }

    @Override
    public boolean canAct(int seat) {
        return !finished && seat == activeSeat;
    }

    @Override
    public List<DraftCommand> legalCommands(int seat) {
        if (!canAct(seat)) return List.of();
        return canPass() ? List.of(new TakePile(seat), new PassPile(seat)) : List.of(new TakePile(seat));
    }

    @Override
    public List<DraftCard> picksOf(int seat) {
        return Collections.unmodifiableList(picks.get(seat));
    }

    /** Passing must leave something to take: a later pile, or a stack card after refilling this one. */
    private boolean canPass() {
        return nextPile(currentPile) >= 0 || stack.size() >= 2;
    }

    private int nextPile(int after) {
        for (int pile = after + 1; pile < PILES; pile++) if (!piles.get(pile).isEmpty()) return pile;
        return -1;
    }

    @Override
    protected void execute(DraftCommand command) {
        int seat = command.seat();
        switch (command) {
            case TakePile ignored -> record(new PileTaken(seat, currentPile, piles.get(currentPile)));
            case PassPile ignored -> {
                if (!canPass()) throw new IllegalArgumentException("Nothing left to pass to: take pile " + (currentPile + 1));
                boolean lastPile = nextPile(currentPile) < 0;
                record(new PilePassed(seat, currentPile));
                if (lastPile) record(new StackCardTaken(seat, stack.peekFirst()));
            }
            default -> throw new IllegalArgumentException("A Winston draft only accepts taking or passing a pile");
        }
        if (stack.isEmpty() && piles.stream().allMatch(List::isEmpty)) record(new DraftFinished());
    }

    @Override
    protected void apply(DraftEvent event) {
        switch (event) {
            case WinstonDraftStarted started -> {
                id = started.draftId();
                stack.addAll(started.stack());
                for (List<DraftCard> pile : piles) pile.add(stack.removeFirst());
                activeSeat = started.firstSeat();
                currentPile = 0;
            }
            case PileTaken taken -> {
                List<DraftCard> pile = piles.get(taken.pile());
                picks.get(taken.seat()).addAll(pile);
                pile.clear();
                if (!stack.isEmpty()) pile.add(stack.removeFirst());
                endTurn();
            }
            case PilePassed passed -> {
                if (!stack.isEmpty()) piles.get(passed.pile()).add(stack.removeFirst());
                int next = nextPile(passed.pile());
                if (next >= 0) currentPile = next;
            }
            case StackCardTaken taken -> {
                picks.get(taken.seat()).add(stack.removeFirst());
                endTurn();
            }
            case DraftFinished ignored -> finished = true;
            default -> throw new IllegalArgumentException("Not a Winston draft event: " + event);
        }
    }

    private void endTurn() {
        activeSeat = (activeSeat + 1) % SEATS;
        int first = nextPile(-1);
        currentPile = Math.max(first, 0);
    }
}
