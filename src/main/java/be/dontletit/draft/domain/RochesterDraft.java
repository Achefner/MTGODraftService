package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.DraftCommand;
import be.dontletit.draft.domain.command.PickCard;
import be.dontletit.draft.domain.event.BoosterDraftStarted;
import be.dontletit.draft.domain.event.CardPicked;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.domain.event.DraftFinished;
import be.dontletit.draft.domain.event.PackOpened;
import be.dontletit.draft.domain.event.RoundStarted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rochester draft: 8 seats, 3 boosters of 15 each. One booster at a time is laid face up and
 * seats pick from it in turn, starting with its opener and going in the round's direction. The
 * last seat picks twice and the order snakes back, so the opener gets one card and everyone else two.
 * Each seat opens one booster per round; openers move in the round's direction, starting from seat 0.
 * Rounds go left, right, left.
 */
public final class RochesterDraft extends Draft {

    public static final int ROUNDS = 3;
    public static final int PACK_SIZE = 15;
    private static final int SEATS = DraftFormat.ROCHESTER.seats();

    private String id;
    private List<List<DraftCard>> boosters;
    private final List<List<DraftCard>> picks = new ArrayList<>();
    private List<DraftCard> table = new ArrayList<>();
    private int round;
    private PassDirection direction = PassDirection.LEFT;
    private int packNumber;
    private int opener;
    private int pickInPack;
    private boolean finished;

    RochesterDraft() {
        for (int seat = 0; seat < SEATS; seat++) picks.add(new ArrayList<>());
    }

    static RochesterDraft start(String id, List<DraftCard> cards) {
        RochesterDraft draft = new RochesterDraft();
        draft.record(new BoosterDraftStarted(id, DraftFormat.ROCHESTER, boosters(cards, SEATS * ROUNDS, PACK_SIZE)));
        draft.startRound(1);
        return draft;
    }

    @Override public String id() { return id; }
    @Override public DraftFormat format() { return DraftFormat.ROCHESTER; }
    @Override public boolean finished() { return finished; }
    public int round() { return round; }
    public PassDirection direction() { return direction; }
    /** Which booster of the round is on the table, from 1 to 8. */
    public int packNumber() { return packNumber; }
    /** The seat that opened the booster on the table. */
    public int opener() { return opener; }
    /** Which pick of the booster on the table comes next, from 1 to 15. */
    public int pickInPack() { return pickInPack; }

    /** The face-up cards everyone can see. */
    public List<DraftCard> table() {
        return Collections.unmodifiableList(table);
    }

    /** The seat whose turn it is: opener, then around the table, then back. */
    public int activeSeat() {
        int step = (pickInPack - 1) % (2 * SEATS);
        int offset = step < SEATS ? step : 2 * SEATS - 1 - step;
        return seatFrom(opener, offset);
    }

    @Override
    public boolean canAct(int seat) {
        return !finished && seat == activeSeat();
    }

    @Override
    public List<DraftCommand> legalCommands(int seat) {
        if (!canAct(seat)) return List.of();
        return table.stream().<DraftCommand>map(card -> new PickCard(seat, card.name())).toList();
    }

    @Override
    public List<DraftCard> picksOf(int seat) {
        return Collections.unmodifiableList(picks.get(seat));
    }

    @Override
    protected void execute(DraftCommand command) {
        if (!(command instanceof PickCard pick)) throw new IllegalArgumentException("A Rochester draft only accepts picks");
        DraftCard card = findCard(table, pick.cardName(), "on the table");
        record(new CardPicked(pick.seat(), card, true));
        if (!table.isEmpty()) return;
        if (packNumber < SEATS) openPack(packNumber + 1);
        else if (round < ROUNDS) startRound(round + 1);
        else record(new DraftFinished());
    }

    private void startRound(int next) {
        record(new RoundStarted(next, next % 2 == 1 ? PassDirection.LEFT : PassDirection.RIGHT));
        openPack(1);
    }

    private void openPack(int number) {
        int nextOpener = seatFrom(0, number - 1);
        record(new PackOpened(round, number, nextOpener, boosters.get((round - 1) * SEATS + nextOpener)));
    }

    private int seatFrom(int seat, int steps) {
        int shift = direction == PassDirection.LEFT ? steps : -steps;
        return Math.floorMod(seat + shift, SEATS);
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
            }
            case PackOpened opened -> {
                packNumber = opened.packNumber();
                opener = opened.opener();
                table = new ArrayList<>(opened.cards());
                pickInPack = 1;
            }
            case CardPicked picked -> {
                table.remove(picked.card());
                picks.get(picked.seat()).add(picked.card());
                pickInPack++;
            }
            case DraftFinished ignored -> finished = true;
            default -> throw new IllegalArgumentException("Not a Rochester draft event: " + event);
        }
    }
}
