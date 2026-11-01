# CLAUDE.md

Hexagonal, event-sourced draft engine (Classic, Rochester, Winston) for tables of people and
random bots, in front of `../MTGODatabase`. Persists to its own postgres (`docker compose up -d`, :5434).

```
be.dontletit.draft/
├── domain/          # Draft (sealed base: handle/replay/history/moveCount) + ClassicDraft, RochesterDraft, WinstonDraft,
│   │                # DraftFormat, PickTimer (time per decision), RandomBot, DraftCard(name, imageUrl, manaValue), ManaValue, PassDirection
│   ├── command/     # DraftCommand: PickCard, TakePile, PassPile (each carries its seat)
│   └── event/       # DraftEvent: *DraftStarted, RoundStarted, PackOpened, CardPicked, PackPassed,
│                    #   PileTaken, PilePassed, StackCardTaken, DraftFinished
├── api/             # DraftApiPort (tables, seats, actions, subscribe) + views, DraftUpdate/DraftListener/Subscription,
│                    #   SeatAccessDeniedException (403)
├── spi/             # CardPoolSpiPort, DraftRepositorySpiPort (TableRecord/PlayerRecord/TableStatus + events), TimerSpiPort
├── application/     # DraftApiAdapter (tables, tokens, bots, clocks, listeners), DraftSession (one table in memory),
│                    #   DraftViews (what a seat sees), EventProjection (per-seat event filter), SeatTokens
└── infrastructure/
    ├── rest/        # DraftController (/drafts, stream as SSE), StreamConnections (SSE sinks + heartbeat), ErrorMappers
    ├── data/        # MtgoDatabaseClient + MtgoDatabaseCardPool, JpaDraftRepository + entities, EventCodec (JSON by class name)
    ├── timer/       # SystemTimer (ScheduledExecutorService)
    └── config/      # DraftConfig (PickTimer from draft.timer.*), DraftStartup (resume running drafts)
```

```bash
docker compose up -d   # postgres :5434, db/user/password mtgodraft; Flyway migrates at startup (db/migration)
mvn quarkus:dev        # :8082 (test port :8092); needs MTGODatabase on :8080
mvn test               # domain + application are plain JUnit on stubs (InMemoryDraftRepository, ManualTimer);
                       # @QuarkusTest on H2 for the repository, controller and SSE stream
```

- Dependency rule, enforced by `HexagonalArchitectureTest` (reads every main file's imports):
  domain and api depend on nothing else; spi only on the domain; application on the ports, the
  domain and CDI annotations, never on infrastructure; rest reaches the core only through the API
  port; data and timer adapters never touch api, application or rest; config never uses application.
- Every rule lives in the domain. `Draft.handle(command)` validates, then the format's `execute`
  calls `record(event)`, which runs `apply(event)` and appends to the history. `apply` must stay
  deterministic: `Draft.replay(history)` rebuilds a draft with `apply` alone. Randomness (shuffle,
  Winston's first seat, bot and timeout choices) ends up inside events.
- The domain does not know who is human. The adapter keeps players (seat, name, token hash) on the
  table; seats without a player are bots once the draft runs.
- `DraftApiAdapter.play` is the one path for every move (a person, a timeout): handle, bots, append
  to the repository, then rearm clocks and notify listeners. If storing fails, the session is
  rebuilt from the repository, so memory never runs ahead of the database.
- A session is used only inside `synchronized (session)`; timers run on their own thread.
- Clocks belong to a seat's move count: a clock armed for move 3 does nothing once the seat moved.
  After a restart clocks are rearmed with a full time (deadlines are not stored).
- Anything sent to a seat goes through `DraftViews` / `EventProjection.forSeat`: no hidden cards.
- New event kinds need a case in `EventCodecTest` (it checks every permitted subclass round-trips).
- Schema changes go in a new `db/migration/V<n>__*.sql`; it must run on postgres and H2 (tests).
- `DraftCard.manaValue` is an Integer: null for cards in events stored before it existed (old JSON
  has no such field). `MtgoDatabaseCardPool` computes it with `ManaValue.of(manaCost)`.
- Random drafts use MTGODatabase's `/cards/random/{count}`, which samples with replacement, so a
  random pool may contain the same card twice; that is accepted.
