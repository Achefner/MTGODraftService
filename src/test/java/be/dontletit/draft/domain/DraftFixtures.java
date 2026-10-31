package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.DraftCommand;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/** Shared helpers for the draft format tests. */
final class DraftFixtures {

    private DraftFixtures() {}

    static List<DraftCard> pool(int size) {
        return IntStream.range(0, size).mapToObj(i -> new DraftCard("Card " + i, "https://img/" + i + ".jpg")).toList();
    }

    static Draft start(DraftFormat format, long seed) {
        return Draft.start(format, "d1", pool(format.cardsNeeded()), new Random(seed));
    }

    /** Lets every seat but {@code seat} play its first legal command until none of them can act. */
    static void othersPlayUntilBlocked(Draft draft, int seat) {
        boolean acted = true;
        while (acted && !draft.finished()) {
            acted = false;
            for (int other = 0; other < draft.format().seats(); other++) {
                if (other == seat || !draft.canAct(other)) continue;
                List<DraftCommand> legal = draft.legalCommands(other);
                draft.handle(legal.get(0));
                acted = true;
                if (draft.finished()) return;
            }
        }
    }

    /** Plays the whole draft with every seat taking its first legal command. */
    static void playToTheEnd(Draft draft) {
        while (!draft.finished()) othersPlayUntilBlocked(draft, -1);
    }
}
