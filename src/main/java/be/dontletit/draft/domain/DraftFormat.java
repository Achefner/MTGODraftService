package be.dontletit.draft.domain;

/** The ways to draft, with how many seats they use and how many cards they need. */
public enum DraftFormat {
    CLASSIC("Classic", 8, 360,
            "8 players open 3 boosters of 15 cards, one per round, pick one card and pass the rest: left, then right, then left."),
    ROCHESTER("Rochester", 8, 360,
            "8 players, 3 boosters of 15 each. One booster at a time is laid face up; players pick in turn, the last one picks twice and the order snakes back."),
    WINSTON("Winston", 2, 90,
            "2 players, 90 cards in a stack and 3 face-down piles. Look at each pile in turn: take it, or add a card to it and move on.");

    private final String displayName;
    private final int seats;
    private final int cardsNeeded;
    private final String description;

    DraftFormat(String displayName, int seats, int cardsNeeded, String description) {
        this.displayName = displayName;
        this.seats = seats;
        this.cardsNeeded = cardsNeeded;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public int seats() { return seats; }
    public int cardsNeeded() { return cardsNeeded; }
    public String description() { return description; }
}
