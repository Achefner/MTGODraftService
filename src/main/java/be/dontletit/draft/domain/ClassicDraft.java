package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.DraftCommand;
import be.dontletit.draft.domain.command.PickCard;
import be.dontletit.draft.domain.event.BoosterDraftStarted;
import be.dontletit.draft.domain.event.CardPicked;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.domain.event.DraftFinished;
import be.dontletit.draft.domain.event.PackPassed;
import be.dontletit.draft.domain.event.RoundStarted;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Booster draft: every seat opens a 15-card booster per round, picks one card and passes the rest,
 * to the left (seat + 1) in rounds 1 and 3 and to the right in round 2. Seats pick at their own pace:
 * passed packs wait in the neighbour's queue. A round ends when every pack is empty.
 */
public final class ClassicDraft extends Draft {

    public static final int ROUNDS = 3;
    public static final int PACK_SIZE = 15;
    private static final int SEATS = DraftFormat.CLASSIC.seats();

    private String id;
    private List<List<DraftCard>> boosters;
    private final List<Deque<List<DraftCard>>> queues = new ArrayList<>();
    private final List<List<DraftCard>> picks = new ArrayList<>();
    private int round;
    private PassDirection direction = PassDirection.LEFT;
    private boolean finished;

    ClassicDraft() {
        for (int seat = 0; seat < SEATS; seat++) {
            queues.add(new ArrayDeque<>());
            picks.add(new ArrayList<>());
        }
    }

    static ClassicDraft start(String id, List<DraftCard> cards) {
        ClassicDraft draft = new ClassicDraft();
        draft.record(new BoosterDraftStarted(id, DraftFormat.CLASSIC, boosters(cards, SEATS * ROUNDS, PACK_SIZE)));
        draft.record(new RoundStarted(1, PassDirection.LEFT));
        return draft;
    }

    @Override public String id() { return id; }
    @Override public DraftFormat format() { return DraftFormat.CLASSIC; }
    @Override public boolean finished() { return finished; }
    public int round() { return round; }
    public PassDirection direction() { return direction; }

    /** The pack the seat picks from now: the oldest one in its queue, or empty while it waits. */
    public List<DraftCard> packFor(int seat) {
        List<DraftCard> pack = queues.get(seat).peekFirst();
        return pack == null ? List.of() : Collections.unmodifiableList(pack);
    }

    /** How many packs wait in front of the seat. */
    public int queuedPacks(int seat) {
        return queues.get(seat).size();
    }

    /** The seat's pick number in the current round, from 1 to 15. */
    public int pickNumber(int seat) {
        return Math.min(picks.get(seat).size() - (round - 1) * PACK_SIZE + 1, PACK_SIZE);
    }

    @Override
    public boolean canAct(int seat) {
        return !finished && !queues.get(seat).isEmpty();
    }

    @Override
    public List<DraftCommand> legalCommands(int seat) {
        if (!canAct(seat)) return List.of();
        return packFor(seat).stream().<DraftCommand>map(card -> new PickCard(seat, card.name())).toList();
    }

    @Override
    public List<DraftCard> picksOf(int seat) {
        return Collections.unmodifiableList(picks.get(seat));
    }

    @Override
    protected void execute(DraftCommand command) {
        if (!(command instanceof PickCard pick)) throw new IllegalArgumentException("A Classic draft only accepts picks");
        int seat = pick.seat();
        DraftCard card = findCard(packFor(seat), pick.cardName(), "in your pack");
        int left = packFor(seat).size() - 1;
        record(new CardPicked(seat, card, false));
        if (left > 0) record(new PackPassed(seat, neighbour(seat), left));
        if (queues.stream().allMatch(Deque::isEmpty)) {
            if (round < ROUNDS) record(new RoundStarted(round + 1, round % 2 == 1 ? PassDirection.RIGHT : PassDirection.LEFT));
            else record(new DraftFinished());
        }
    }

    private int neighbour(int seat) {
        return direction == PassDirection.LEFT ? (seat + 1) % SEATS : (seat + SEATS - 1) % SEATS;
    }

    @Override
    protected void apply(DraftEvent event) {
        switch (event) {
            case BoosterDraftStarted started -> {
                id = started.draftId();
                boosters = started.packs();
            }
            case RoundStarted started -> {
                round = started.round();
                direction = started.direction();
                for (int seat = 0; seat < SEATS; seat++) {
                    queues.get(seat).addLast(new ArrayList<>(boosters.get((round - 1) * SEATS + seat)));
                }
            }
            case CardPicked picked -> {
                List<DraftCard> pack = queues.get(picked.seat()).peekFirst();
                pack.remove(picked.card());
                picks.get(picked.seat()).add(picked.card());
                if (pack.isEmpty()) queues.get(picked.seat()).removeFirst();
            }
            case PackPassed passed -> queues.get(passed.toSeat()).addLast(queues.get(passed.fromSeat()).removeFirst());
            case DraftFinished ignored -> finished = true;
            default -> throw new IllegalArgumentException("Not a Classic draft event: " + event);
        }
    }
}
