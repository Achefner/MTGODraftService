package be.dontletit.draft.domain.command;

/** Something a seat asks to do. A draft turns a valid command into events. */
public sealed interface DraftCommand permits PickCard, TakePile, PassPile {
    int seat();
}
