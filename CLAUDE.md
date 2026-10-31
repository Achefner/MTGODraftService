# CLAUDE.md

Hexagonal, event-sourced draft engine (Classic, Rochester, Winston) for one human seat plus
random bots, in front of `../MTGODatabase`.

```
be.dontletit.draft/
├── domain/          # Draft (sealed base: handle/replay/history) + ClassicDraft, RochesterDraft, WinstonDraft,
│   │                # DraftFormat (seats, cards needed), RandomBot, DraftCard(name, imageUrl), PassDirection
│   ├── command/     # DraftCommand: PickCard, TakePile, PassPile (each carries its seat)
│   └── event/       # DraftEvent: *DraftStarted, RoundStarted, PackOpened, CardPicked, PackPassed,
│                    #   PileTaken, PilePassed, StackCardTaken, DraftFinished
├── api/             # DraftApiPort + views (DraftView, DraftEventView, WinstonView, SeatView, DraftFormatView, DraftAction…)
├── spi/             # CardPoolSpiPort (random cards, collection cards, cubes, save deck), DraftStoreSpiPort, CubeInfo
├── application/     # DraftApiAdapter (human seat 0, bots, views), EventProjection (what a seat may see of an event)
└── infrastructure/
    ├── rest/        # DraftController (/drafts), request records, ErrorMappers (400/404/409/502)
    └── data/        # MtgoDatabaseClient (REST client, config key mtgo-database), MtgoDatabaseCardPool, InMemoryDraftStore, DTOs
```

```bash
mvn quarkus:dev     # :8082 (test port :8092); needs MTGODatabase on :8080
mvn test            # domain + application are plain JUnit; @QuarkusTest for the SPI and the controller with @InjectMock on the REST client
```

- Every rule lives in the domain. `Draft.handle(command)` validates (seat exists, draft not over,
  `canAct(seat)`), then the format's `execute` calls `record(event)`, which runs `apply(event)`
  and appends to the history. `apply` must stay deterministic and only change state:
  `Draft.replay(history)` rebuilds a draft with `apply` alone. Randomness (shuffle, Winston's
  first seat) happens in `Draft.start` and is captured in the start event.
- The domain does not know who is human. Seats are numbers. `legalCommands(seat)` lists what a
  seat may do; `RandomBot` picks among them, so it works for every format.
- Classic seats have a queue of packs, so seats can pick at different speeds (needed for multiplayer).
  "Left" is seat + 1.
- Events can carry private information (Classic picks, Winston piles). Anything sent to a player
  must go through `EventProjection.forSeat`.
- `DraftFormat.cardsNeeded()` is checked by the adapter for cubes (clear 400 message) and by `Draft.start`.
- Random drafts use MTGODatabase's `/cards/random/{count}`, which samples with replacement, so a
  random pool may contain the same card twice; that is accepted (boosters do too).
- The service does not persist drafts (InMemoryDraftStore); a restart loses sessions in progress.
  The event history is what a persistent store should keep.
