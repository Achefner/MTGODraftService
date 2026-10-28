# CLAUDE.md

Hexagonal draft engine for one human seat plus seven random bots, in front of `../MTGODatabase`.

```
be.dontletit.draft/
├── domain/          # Draft (all the rules: dealing, picking, passing, rounds), DraftCard(name, imageUrl), PassDirection
├── api/             # DraftApiPort + view records (DraftView, DraftCardView, DraftSourceView, SavedDeckView)
├── spi/             # CardPoolSpiPort (random cards, collection cards, cubes, save deck), DraftStoreSpiPort, CubeInfo
├── application/     # DraftApiAdapter: starts drafts, delegates picks to the domain, maps views
└── infrastructure/
    ├── rest/        # DraftController (/drafts), request records, ErrorMappers (400/404/409/502)
    └── data/        # MtgoDatabaseClient (REST client, config key mtgo-database), MtgoDatabaseCardPool, InMemoryDraftStore, DTOs
```

```bash
mvn quarkus:dev     # :8082 (test port :8092); needs MTGODatabase on :8080
mvn test            # domain + adapter are plain JUnit; @QuarkusTest for the SPI and the controller with @InjectMock on the REST client
```

- Keep every rule in `Draft`: it is pure Java, deterministic for a given `Random` (tests use seeds),
  and the only place that knows seats, rounds and pass directions. The adapter only maps and stores.
- "Left" is seat + 1. The domain exposes `packAt(seat)` / `picksOf(seat)` for tests and future UIs.
- `Draft.CARDS_NEEDED` (360) is checked by the adapter for cubes (clear 400 message) and by the domain.
- Random drafts use MTGODatabase's `/cards/random/{count}`, which samples with replacement, so a
  random pool may contain the same card twice; that is accepted (boosters do too).
- The service does not persist drafts (InMemoryDraftStore); a restart loses sessions in progress.
