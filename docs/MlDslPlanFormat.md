# MLDSL plan.json format (MVP)

The command `/mldsl run [path] [--start N]` reads a JSON plan (default: `.minecraft/plan.json`) and executes it by
feeding the generated tokens into the existing `/placeadvanced` pipeline.

## Option A: raw placeadvanced tokens

```json
{
  "placeadvanced": [
    "diamond_block", "\"вход\"", "no",
    "cobblestone", "\"Сообщение\"", "\"slot(27)=text(Hello)\""
  ]
}
```

Notes:
- `name` and `args` tokens must be quoted (`"..."`) if they contain spaces.
- Quotes inside tokens are not supported (the underlying parser has no escaping).

## Option B: entries/steps list

```json
{
  "entries": [
    { "block": "diamond_block", "name": "вход", "args": "no" },
    { "block": "cobblestone", "name": "Сообщение", "args": "slot(27)=text(Hello)" }
  ]
}
```

You can also use `"steps"` instead of `"entries"`.

Special entry:
- `{ "block": "air" }` inserts a pause (same as `air` in `/placeadvanced`).

## Multiline (multiple rows)

To place multiple separate "rows" of code blocks in one plan, insert a row break:

- In `entries`: `{ "block": "newline" }`
- In raw tokens: `"newline"`

The runner will pick the next recorded blue glass position and start a new `/placeadvanced` line from there.

Alternative format:
```json
{
  "rows": [
    ["diamond_block", "\"вход\"", "no", "cobblestone", "\"Сообщение\"", "\"slot(27)=text(Hello)\""],
    ["diamond_block", "\"выход\"", "no", "cobblestone", "\"Сообщение\"", "\"slot(27)=text(Bye)\""]
  ]
}
```

## DSL blocks -> plan rows

The MLDSL compiler emits one row per top-level block:
- `event(name) { ... }` -> row starts with `diamond_block` and `name` (e.g. `вход`)
- `func name { ... }` -> row starts with `lapis_block` and `name`
- `loop name ticks { ... }` -> row starts with `emerald_block`, sets its name, then sets tick period (min 5)
