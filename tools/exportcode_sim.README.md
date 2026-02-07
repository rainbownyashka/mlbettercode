# Exportcode Simulator

`tools/exportcode_sim.py` reproduces BetterCode export row scanning without Minecraft.

It helps debug:
- row traversal (`-2 x` per step),
- empty-pair stop condition,
- side piston emission,
- chest snapshot behavior (`cache` vs `nocache`).

## Run

```bash
python tools/exportcode_sim.py tools/_sim_case.json --debug -o tools/_sim_case.out.json
python tools/exportcode_sim.py tools/_sim_case.json --debug --no-cache
```

## Input format

```json
{
  "scopeKey": "ID:793426:0",
  "glasses": [{"x":219,"y":0,"z":207}],
  "nodes": {
    "219,1,207": {"block":"minecraft:diamond_block"},
    "219,1,206": {"sign":["Событие игрока","Вход","",""]},
    "217,2,207": {
      "block":"minecraft:chest",
      "chest": {"title":"Сундук","size":27,"slots":[{"slot":13,"id":"minecraft:slime_ball","count":1,"displayName":"§c3"}]}
    },
    "214,1,207": {"block":"minecraft:piston","facing":"west"}
  },
  "chestCache": {
    "0:217,2,207": {
      "title":"Cached Chest",
      "size":27,
      "slots":[{"slot":13,"id":"minecraft:slime_ball","count":1,"displayName":"§c3"}]
    }
  }
}
```

Notes:
- `nodes` key is exact world coordinate `"x,y,z"`.
- Signs are read from `entryPos + (0,0,-1)` (`findSignAtZMinus1` parity).
- Chest is read strictly from `entryPos + (0,1,0)`.
- `--no-cache` disables fallback to `chestCache`.

