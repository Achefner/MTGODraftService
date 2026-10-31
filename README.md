# MTGODraftService

Backend of the [MTGODraftClient](../MTGODraftClient): runs simulated drafts for one human
player against random bots. Hexagonal: the domain knows only card names and image URLs.
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

Seat 0 is the human; every other seat plays a random legal move. After each human action
the bots play until it is the human's turn again. In Classic they can run ahead of the human.

A draft draws from random database cards, or from a CUBE collection of MTGODatabase with at
least the format's card count. A bigger cube is shuffled and the first cards are used. Picks
can be saved back to MTGODatabase as a DECK collection.

## Endpoints (port 8082)

| Method | Path                  | Body / returns |
|--------|-----------------------|----------------|
| GET    | `/drafts/formats`     | `[ {name, displayName, seats, cardsNeeded, description} ]` |
| GET    | `/drafts/sources`     | cube collections `[ {id, name, cardCount} ]` |
| POST   | `/drafts`             | `{"source":"random"\|"cube", "collectionId":"…", "format":"CLASSIC"\|"ROCHESTER"\|"WINSTON"}` (format defaults to CLASSIC) → 201 draft view |
| GET    | `/drafts/{id}`        | draft view |
| POST   | `/drafts/{id}/actions`| `{"type":"PICK","cardName":"…"}`, `{"type":"TAKE_PILE"}` or `{"type":"PASS_PILE"}` → draft view after the bots played |
| POST   | `/drafts/{id}/picks`  | `{"cardName":"…"}`: shortcut for a PICK action |
| POST   | `/drafts/{id}/deck`   | `{"name":"…"}` → 201 `{collectionId, name, cardCount}` |

Draft view:

```
{ id, source, format, seat, yourTurn, finished,
  round, pickNumber, direction,          // Classic and Rochester (pickNumber = pick in the Rochester booster)
  packNumber, opener, activeSeat,        // Rochester (activeSeat also for Winston)
  pack: [{name, imageUrl}],              // Classic: your pack; Rochester: the table; Winston: the pile you look at
  picks: [...],
  winston: {pileSizes, currentPile, stackSize},   // Winston only (currentPile is 0-based)
  seats: [{seat, you, pickCount}],
  log: [{type, seat, round, direction, packNumber, pile, cardCount, cards}] }  // last 30 events you may see
```

Fields that do not apply are null. The log never shows hidden information: other seats'
Classic picks or the cards of a Winston pile the opponent took.

Errors are `{"error": "…"}`: 400 (unknown format or action, card not there, cube too small,
illegal pass), 404 (unknown draft or collection), 409 (draft finished), 502 (MTGODatabase
unreachable or failing).

Drafts are kept in memory and vanish when the service restarts.

## Run

```bash
mvn quarkus:dev        # http://localhost:8082, expects MTGODatabase on http://localhost:8080
mvn test
```
