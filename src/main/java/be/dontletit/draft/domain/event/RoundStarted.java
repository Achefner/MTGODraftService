package be.dontletit.draft.domain.event;

import be.dontletit.draft.domain.PassDirection;

/** Classic: every seat opens its booster for the round. Rochester: the round's boosters will be opened one by one. */
public record RoundStarted(int round, PassDirection direction) implements DraftEvent {}
