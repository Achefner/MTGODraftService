package be.dontletit.draft.domain;

import java.time.Duration;

/**
 * How long a seat has for its current decision. Like Magic Online, Classic gives less time as the
 * pack shrinks: {@code classicSecondsPerCard} per card in the pack, at least {@code classicMinSeconds}.
 * Rochester gives more time for the first pick of a booster, to read the table. Winston gives the
 * same time for every take or pass.
 */
public record PickTimer(int classicSecondsPerCard, int classicMinSeconds,
                        int rochesterFirstPickSeconds, int rochesterPickSeconds, int winstonMoveSeconds) {

    public static final PickTimer DEFAULT = new PickTimer(5, 10, 40, 20, 30);

    public PickTimer {
        if (classicSecondsPerCard <= 0 || classicMinSeconds <= 0 || rochesterFirstPickSeconds <= 0
                || rochesterPickSeconds <= 0 || winstonMoveSeconds <= 0) {
            throw new IllegalArgumentException("Timer settings must be positive");
        }
    }

    public Duration timeFor(Draft draft, int seat) {
        return switch (draft) {
            case ClassicDraft classic -> forPackSize(classic.packFor(seat).size());
            case RochesterDraft rochester -> Duration.ofSeconds(rochester.pickInPack() == 1 ? rochesterFirstPickSeconds : rochesterPickSeconds);
            case WinstonDraft ignored -> Duration.ofSeconds(winstonMoveSeconds);
        };
    }

    Duration forPackSize(int cards) {
        return Duration.ofSeconds(Math.max(classicMinSeconds, (long) cards * classicSecondsPerCard));
    }
}
