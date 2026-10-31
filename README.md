# MTGODraftService

Backend of the [MTGODraftClient](../MTGODraftClient): runs drafts for people and random
bots at the same table. Hexagonal: the domain knows only card names and image URLs.
The domain is event sourced: every action becomes events, and a draft can be rebuilt by
replaying them. That is the base for multiplayer drafting later.

## Formats

| Format    | Seats | Cards | Rules implemented |
|-----------|-------|-------|-------------------|
| CLASSIC   | 8     | 360   | Every seat opens a 15-card booster per round, picks one card and passes the rest: left (seat + 1) in rounds 1 and 3, right in round 2. Seats pick at their own pace: passed packs wait in the neighbour's queue. The next round opens when every pack is empty. 45 picks each. |
| ROCHESTER | 8     | 360   | One booster at a time is laid face up. Its opener picks first, then the turn goes round the table in the round's direction; the last seat picks twice and the order snakes back, so the opener gets one card and everyone else two. Each seat opens one booster per round; openers move in the round's direction starting from seat 0. Rounds go left, right, left. Every pick is public. 45 picks each. |
| WINSTON   | 2     | 90    | 90 cards form the stack; the top three start three face-down piles. On a turn, look at pile 1: take it (it is refilled from the stack) or pass it (a stack card is added to it) and look at the next pile. Passing pile 3 means taking the top card of the stack. The first player is chosen at random. |

Choices made where the rules are silent:

- Rochester: seat 0 opens the first booster of every round.
- Winston, once the stack runs out: passing adds nothing, empty piles are skipped, and a
  seat must take the pile when passing would leave nothing to take. This also applies to
  pile 3 with a single stack card left, since it would go onto the pile and leave nothing to draw.

## Tables, seats and bots

Whoever opens a table sits at seat 0 and hosts it. Others join through the table id, usually
an invite link, until the host starts; seats nobody took are then played by random bots. A
solo draft is the same thing started at once. Each seat gets a secret token when it is taken.
Every call about the seat carries it, and only its SHA-256 hash is stored. After a person's
move the bots play as far as they can; in Classic they may run ahead of the humans.

## Pick timer

A timed table, the default, gives each human seat a clock for its current decision, as Magic
Online does. When it runs out, a random legal move is made for the seat.

| Format    | Time per decision |
|-----------|-------------------|
| CLASSIC   | 5 s per card in the pack, at least 10 s: 75 s for a full pack |
| ROCHESTER | 40 s for the first pick of a booster, to read the table; 20 s after |
| WINSTON   | 30 s per take or pass |

The settings are `draft.timer.*` in `application.properties`.

## Disconnections and restarts

- **Live updates** are server-sent events, one stream per seat. The first event carries the
  whole view; then an `update` event follows every change, with the events the seat may see
  and its new view. The event id is the draft's version: a browser that reconnects sends it back
  as `Last-Event-ID` and gets what it missed. A comment line every 15 seconds keeps idle
  connections open and reveals dead ones; a seat shows as offline within about that time.
- **Coming back** works from any tab or device holding the seat token.
- **Lost or repeated actions**: an action carries `expectedMoves`, the `moves` of the view it
  was made on. If the seat has moved since, the service answers 409 instead of playing it twice.
- **Restarts**: tables, players and every draft event are stored in postgres. On startup,
  running drafts are rebuilt from their history and their clocks rearmed with a full time.

## Endpoints (port 8082)

Seat calls carry the token in the `X-Seat-Token` header; the stream takes it as `?token=`,
because browsers cannot add headers to an EventSource.

| Method | Path                    | Body / returns |
|--------|-------------------------|----------------|
| GET    | `/drafts/formats`       | `[ {name, displayName, seats, cardsNeeded, description} ]` |
| GET    | `/drafts/sources`       | cube collections `[ {id, name, cardCount} ]` |
| POST   | `/drafts`               | `{source: "random"\|"cube", collectionId, format, playerName, timed (true), startNow (true)}` → 201 `{draftId, seat, token, view}` |
| GET    | `/drafts/{id}/table`    | public preview, no token: `{id, format, source, status, timed, seats}` |
| POST   | `/drafts/{id}/seats`    | `{playerName}` → 201 `{draftId, seat, token, view}`; 409 when started or full |
| POST   | `/drafts/{id}/start`    | host only (403 otherwise) → view; 409 when already started |
| GET    | `/drafts/{id}`          | the seat's view |
| POST   | `/drafts/{id}/actions`  | `{type: "PICK"\|"TAKE_PILE"\|"PASS_PILE", cardName, expectedMoves}` → view after the bots played |
| POST   | `/drafts/{id}/deck`     | `{name}` → 201 `{collectionId, name, cardCount}` |
| GET    | `/drafts/{id}/stream`   | `text/event-stream` of `update` events `{version, events, view}` |

Draft view:

```
{ id, source, format, status (LOBBY|RUNNING|FINISHED), version, seat, host, yourTurn, finished,
  moves,                                 // send it back as expectedMoves
  timer: {deadline, seconds, remainingMillis},    // null when the seat has no clock running
  round, pickNumber, direction,          // Classic and Rochester (pickNumber = pick in the Rochester booster)
  packNumber, opener, activeSeat,        // Rochester (activeSeat also for Winston)
  pack: [{name, imageUrl}],              // Classic: your pack; Rochester: the table; Winston: the pile you look at
  picks: [...],
  winston: {pileSizes, currentPile, stackSize},   // Winston only (currentPile is 0-based)
  seats: [{seat, you, name, kind (HUMAN|BOT|OPEN), connected, pickCount}],
  log: [{type, seat, round, direction, packNumber, pile, cardCount, cards}] }  // last 30 events you may see
```

Fields that do not apply are null. Neither the log nor the stream shows hidden information:
other seats' Classic picks or the cards of a Winston pile someone else took.

Errors are `{"error": "…"}`: 400 (unknown format or action, card not there, cube too small,
illegal pass), 403 (missing or wrong seat token, not the host), 404 (unknown draft or
collection), 409 (out-of-date action, not started, finished, table full), 502 (MTGODatabase
unreachable or failing).

## Run

```bash
docker compose up -d   # the service's own postgres on :5434 (Flyway creates the schema at startup)
mvn quarkus:dev        # http://localhost:8082, expects MTGODatabase on http://localhost:8080
mvn test               # in-memory H2, no docker needed
```
