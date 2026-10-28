# MTGODraftService

Backend of the [MTGODraftClient](../MTGODraftClient): runs a simulated 8-player booster
draft for one human player. Hexagonal: the domain knows only card names and image URLs.

## Draft rules implemented

- 8 seats, 3 rounds, 15-card packs: 360 cards per draft. Seat 0 is the human; the other
  seven pick a random card from their pack after each human pick.
- Packs pass to the left in rounds 1 and 3 and to the right in round 2. When the packs are
  empty the next round's packs are opened; after 45 picks the draft is finished.
- A draft draws from random database cards, or from a CUBE collection of MTGODatabase (at
  least 360 cards; a bigger cube is shuffled and the first 360 are used).
- Picks can be saved back to MTGODatabase as a DECK collection.

## Endpoints (port 8082)

| Method | Path                  | Body / returns                                                                 |
|--------|-----------------------|--------------------------------------------------------------------------------|
| POST   | `/drafts`             | `{"source":"random"}` or `{"source":"cube","collectionId":"…"}` → 201 draft view |
| GET    | `/drafts/sources`     | cube collections `[ {id, name, cardCount} ]`                                   |
| GET    | `/drafts/{id}`        | draft view                                                                     |
| POST   | `/drafts/{id}/picks`  | `{"cardName":"…"}` → draft view after bots picked and packs were passed        |
| POST   | `/drafts/{id}/deck`   | `{"name":"…"}` → 201 `{collectionId, name, cardCount}`                         |

Draft view: `{id, source, round, pickNumber, direction (LEFT|RIGHT), finished, pack: [{name, imageUrl}], picks: [...]}`.
Errors are `{"error": "…"}`: 400 (card not in pack, cube too small, bad input), 404 (unknown
draft / collection), 409 (draft finished), 502 (MTGODatabase unreachable or failing).

Drafts are kept in memory and vanish when the service restarts.

## Run

```bash
mvn quarkus:dev        # http://localhost:8082, expects MTGODatabase on http://localhost:8080
mvn test
```
