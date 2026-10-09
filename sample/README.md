# Sample release

The data the site serves: about a hundred people, their eras, events and connections, and ten moments. The backend's tests, the API tests (`http/`), the end-to-end tests (`e2e/`), `./gradlew bootRun` and `docker compose up`, which mounts it into the backend's container, all use it.

At startup the backend reads the directory in `app.release.dir`, checks it (`ReleaseReader`), resolves its references and sets every life and event in its era (`ReleaseCompiler`, `ReleaseMapper`), then stores it.

[`release.schema.json`](release.schema.json) is the format as a JSON Schema (draft 2020-12): one definition per kind of record in `$defs`, to validate a line or a moment file against. It is generated from the record classes in `backend/core`: run `./gradlew :core:releaseSchema` in `backend/` after changing them. The backend's tests fail when it is out of date, or when a record in `sample/` does not match it.

## Files

| File | One record per | Points at |
|---|---|---|
| `regions.jsonl` | region, by today's borders (`FR`) | |
| `eras.jsonl` | who governed a region, from `start` to `end` (no `end`: until today) | region |
| `people.jsonl` | person | region |
| `lives.jsonl` | typical life, set in the era that covers its `start` | region |
| `events.jsonl` | dated, sourced event, set in the era that covers its `year` | region, people |
| `connections.jsonl` | two people, how they knew each other, when, and the source | people |
| `moments/<id>.json` | moment: the world around it, its story cards and its doors | region, people, lives, events, moments |

## Rules

- **One record per line, sorted by `id`**, so a change to one record is a change to one line. Connections have no id: they are sorted by their two people, then by `kind`. Moments are nested, so each one is a file of its own, named after its id.
- **References are ids**: `"region": "FR"`, `"person": "emile-zola"`. Every one must resolve.
- **Years are integers**, negative for BCE. There is no year 0.
- **Optional fields are left out** rather than set to `null` or `false`.
- **Strict**: an unknown field, a missing one, a duplicate id or a line out of order fails the start, with the file and line.

Consecutive eras share their boundary year; the one that starts later wins it. A person's `slug`, used in addresses, is their name without accents or punctuation.
