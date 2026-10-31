package be.dontletit.draft.api;

/** Receives a seat's updates. Throwing ends the subscription (the connection is gone). */
@FunctionalInterface
public interface DraftListener {
    void onUpdate(DraftUpdate update);
}
