package be.dontletit.draft.spi;

/** A person sitting at a table; only a hash of the seat token is kept. */
public record PlayerRecord(int seat, String name, String tokenHash) {}
