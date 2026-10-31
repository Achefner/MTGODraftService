-- A table: what is drafted and in which state (LOBBY, RUNNING, FINISHED).
CREATE TABLE draft_tables (
    id          VARCHAR(36)              PRIMARY KEY,
    format      VARCHAR(16)              NOT NULL,
    source      VARCHAR(80)              NOT NULL,
    status      VARCHAR(16)              NOT NULL,
    timed       BOOLEAN                  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

-- People sitting at a table. Only a hash of the seat token is kept.
CREATE TABLE draft_players (
    draft_id    VARCHAR(36)  NOT NULL REFERENCES draft_tables (id),
    seat        INTEGER      NOT NULL,
    name        VARCHAR(40)  NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    PRIMARY KEY (draft_id, seat)
);

-- The draft history: one row per event, in order. The primary key refuses a second writer.
CREATE TABLE draft_events (
    draft_id     VARCHAR(36)              NOT NULL REFERENCES draft_tables (id),
    event_index  INTEGER                  NOT NULL,
    type         VARCHAR(40)              NOT NULL,
    payload      TEXT                     NOT NULL,
    recorded_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (draft_id, event_index)
);
