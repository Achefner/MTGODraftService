package be.dontletit.draft.domain.event;

/**
 * Something that happened in a draft. Replaying a draft's events in order rebuilds its state.
 * Some events carry private information (a classic pick, a Winston pile): publishing them to
 * other players needs a per-seat filter.
 */
public sealed interface DraftEvent permits BoosterDraftStarted, WinstonDraftStarted, RoundStarted, PackOpened,
        CardPicked, PackPassed, PileTaken, PilePassed, StackCardTaken, DraftFinished {}
