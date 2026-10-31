package be.dontletit.draft.api;

/** Stops the updates; the seat shows as disconnected once it has no subscription left. */
@FunctionalInterface
public interface Subscription {
    void close();
}
