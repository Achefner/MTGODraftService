package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.DraftCommand;

import java.util.List;
import java.util.Random;

/** A seat that plays one of its legal commands at random, in any format. */
public final class RandomBot {

    private final Random random;

    public RandomBot(Random random) {
        this.random = random;
    }

    /** @throws IllegalStateException when the seat has nothing to do */
    public DraftCommand choose(Draft draft, int seat) {
        List<DraftCommand> legal = draft.legalCommands(seat);
        if (legal.isEmpty()) throw new IllegalStateException("Seat " + seat + " has nothing to do");
        return legal.get(random.nextInt(legal.size()));
    }

    /** Lets every seat except {@code humanSeat} play until none of them can act. */
    public void playUntilBlocked(Draft draft, int humanSeat) {
        boolean acted = true;
        while (acted && !draft.finished()) {
            acted = false;
            for (int seat = 0; seat < draft.format().seats() && !draft.finished(); seat++) {
                if (seat == humanSeat || !draft.canAct(seat)) continue;
                draft.handle(choose(draft, seat));
                acted = true;
            }
        }
    }
}
