package be.dontletit.draft.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A booster draft: {@value #SEATS} seats, {@value #ROUNDS} rounds of {@value #PACK_SIZE}-card packs.
 * Seat {@value #HUMAN_SEAT} is the human player; the other seats pick at random.
 * Packs are passed to the left (seat + 1) in rounds 1 and 3 and to the right in round 2.
 */
public class Draft {

    public static final int SEATS = 8;
    public static final int ROUNDS = 3;
    public static final int PACK_SIZE = 15;
    public static final int CARDS_NEEDED = SEATS * ROUNDS * PACK_SIZE;
    public static final int HUMAN_SEAT = 0;

    private final String id;
    private final Random random;
    private final List<List<DraftCard>> packsToOpen;   // 24 sealed packs, dealt round by round
    private final List<List<DraftCard>> packs;         // the pack currently in front of each seat
    private final List<List<DraftCard>> picks;         // picks per seat
    private int round;
    private int pickNumber;
    private boolean finished;

    private Draft(String id, List<DraftCard> cards, Random random) {
        this.id = id;
        this.random = random;
        List<DraftCard> shuffled = new ArrayList<>(cards);
        Collections.shuffle(shuffled, random);
        this.packsToOpen = new ArrayList<>();
        for (int i = 0; i < SEATS * ROUNDS; i++) {
            packsToOpen.add(new ArrayList<>(shuffled.subList(i * PACK_SIZE, (i + 1) * PACK_SIZE)));
        }
        this.packs = new ArrayList<>();
        this.picks = new ArrayList<>();
        for (int seat = 0; seat < SEATS; seat++) {
            packs.add(new ArrayList<>());
            picks.add(new ArrayList<>());
        }
        this.round = 0;
        openNextRound();
    }

    /** Starts a draft from a pool of at least {@value #CARDS_NEEDED} cards; extra cards are left out. */
    public static Draft start(String id, List<DraftCard> cards, Random random) {
        if (cards == null || cards.size() < CARDS_NEEDED) {
            int size = cards == null ? 0 : cards.size();
            throw new IllegalArgumentException("A draft needs " + CARDS_NEEDED + " cards, got " + size);
        }
        return new Draft(id, cards, random);
    }

    public String id() { return id; }
    public int round() { return round; }
    public int pickNumber() { return pickNumber; }
    public boolean finished() { return finished; }

    public PassDirection direction() {
        return round % 2 == 1 ? PassDirection.LEFT : PassDirection.RIGHT;
    }

    /** The pack in front of the human player. */
    public List<DraftCard> currentPack() {
        return packAt(HUMAN_SEAT);
    }

    public List<DraftCard> packAt(int seat) {
        return Collections.unmodifiableList(packs.get(seat));
    }

    /** The human player's picks, in pick order. */
    public List<DraftCard> picks() {
        return picksOf(HUMAN_SEAT);
    }

    public List<DraftCard> picksOf(int seat) {
        return Collections.unmodifiableList(picks.get(seat));
    }

    /**
     * The human picks the named card from their pack, every bot picks a random card from
     * theirs, then packs are passed. When the packs are empty the next round opens.
     */
    public void pick(String cardName) {
        if (finished) throw new IllegalStateException("The draft is over");
        DraftCard choice = findInCurrentPack(cardName);

        take(HUMAN_SEAT, choice);
        for (int seat = 0; seat < SEATS; seat++) {
            if (seat == HUMAN_SEAT) continue;
            List<DraftCard> pack = packs.get(seat);
            take(seat, pack.get(random.nextInt(pack.size())));
        }

        if (packs.get(HUMAN_SEAT).isEmpty()) {
            if (round == ROUNDS) finished = true;
            else openNextRound();
        } else {
            passPacks();
            pickNumber++;
        }
    }

    private DraftCard findInCurrentPack(String cardName) {
        if (cardName != null) {
            for (DraftCard card : packs.get(HUMAN_SEAT)) {
                if (card.name().equalsIgnoreCase(cardName.trim())) return card;
            }
        }
        throw new IllegalArgumentException("Card is not in your pack: " + cardName);
    }

    private void take(int seat, DraftCard card) {
        packs.get(seat).remove(card);
        picks.get(seat).add(card);
    }

    private void passPacks() {
        List<List<DraftCard>> passed = new ArrayList<>(packs);
        int shift = direction() == PassDirection.LEFT ? 1 : SEATS - 1;
        for (int seat = 0; seat < SEATS; seat++) {
            packs.set((seat + shift) % SEATS, passed.get(seat));
        }
    }

    private void openNextRound() {
        round++;
        pickNumber = 1;
        for (int seat = 0; seat < SEATS; seat++) {
            packs.set(seat, packsToOpen.get((round - 1) * SEATS + seat));
        }
    }
}
