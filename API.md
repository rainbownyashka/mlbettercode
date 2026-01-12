# MC Python Api - Local HTTP API

Base URL
- API binds to `127.0.0.1` on first free port in `8765..8790`.
- Registry (if available): `http://127.0.0.1:8765`.
- Use `/registry/clients` to discover running instances and their ports.

Notes
- All endpoints are local-only, no auth.
- Client mode means the mod is running on a client. Server mode means running on a dedicated server or singleplayer server.
- `&` color codes are supported in `/bar` and `/bar2` (converted to the Minecraft section sign).
- `/block` returns `id`, `idNum`, and `meta` for loaded chunks only.

Registry
- `GET /registry/clients` (on port 8765)  
  Returns known instances with `name`, `port`, `mode`, `lastSeenMs`.
- `POST /registry/register` (internal heartbeat)
- `GET /registry/ping`

Players
- `GET /player`  
  Returns player list. Uses server list if server present, otherwise client tab list.
- `GET /player/self`  
  Returns current player (client preferred) with coords and port.
- `GET /players/tab`  
  Returns client tab list only.
- `GET /players/coords`  
  Returns coords. Server: all online; Client: only loaded players.

Chat
- `GET /chat/last`  
  Returns last seen chat message + player + time.
- `POST /chat/send`  
  Sends a chat message. If text starts with `/`, runs a command instead.
  Body can be plain text or `text=...`.
  Client-only errors: `chat_open`, `chat_cooldown` (cooldown applies only to API-sent chat/commands).

Commands
- `POST /command`  
  Body: raw command (without `/`), or `cmd` query param.  
  Optional `player=Name` to run as that player if available.
  Client-only errors: `chat_open`, `chat_cooldown` (cooldown applies only to API-sent chat/commands).

Blocks
- `GET /block?x=...&y=...&z=...&dim=...`  
  Returns `{ok:true,id:"minecraft:stone",idNum:1,meta:0}` or `{ok:false,error:"unloaded"}`.

Books
- `POST /book/write`  
  Requires holding `Book & Quill` in main hand.  
  Params:
  - `text=...&pages=N` (fills all pages)
  - or per-page: `p1=...&p2=...&p3=...`
  - `sign=1&title=MyBook` to sign the book.

Bars (client overlay)
- `POST /bar`  
  Params: `text=...&time=3000`
- `POST /bar2`  
  Same as `/bar`, drawn under it.

Client commands (in-game)
- `/cbar <text>` - shows main bar
- `/cbar2 <text>` - shows second bar
- `/gen <name> <start-end>` - fills hotbar with named magma cream

Examples
```text
GET  http://127.0.0.1:8765/registry/clients
GET  http://127.0.0.1:8766/player/self
POST http://127.0.0.1:8766/chat/send    (body: hello)
POST http://127.0.0.1:8766/chat/send    (body: /say hi)
GET  http://127.0.0.1:8766/block?x=0&y=64&z=0
POST http://127.0.0.1:8766/book/write   (body: p1=First&p2=Second)
POST http://127.0.0.1:8766/bar          (body: text=&aHello&time=2000)
```
