# d7oom's-tool — design

**Status:** approved, not yet implemented
**Target pack:** `pack-two` (Shabab 2) — Minecraft 1.20.1 / Forge 47.4.x
**Date:** 2026-07-13

An OP-only build wand: select a region with two right-clicks, see a translucent ghost of the
result before you commit it, then fill / move / clone / save it as a schematic. Every block in
the pack and in vanilla is a valid fill material.

---

## 1. Approach

WorldEdit is the edit engine. `d7oomtool` is the tool.

WorldEdit already solves the hard, dangerous parts — chunk-batched edits that don't stall a
387-mod server, `.schem` serialization that round-trips modded blockstates, an undo history, and
a block argument that tab-completes the entire registry. Reimplementing that is roughly 4000
lines of the highest-risk code in the project. So we don't.

`d7oomtool` supplies only what WorldEdit cannot:

| Gap | Why WorldEdit can't |
|---|---|
| A distinct, unforgeable, OP-only item | WorldEdit's wand is any vanilla wooden axe |
| Right-click / right-click selection | WorldEdit's wand is left-click pos1, right-click pos2 |
| Ghost-block hologram | WorldEdit has no client renderer; the CUI port draws a wireframe only |
| Drag-move / drag-clone to an arbitrary point | `//move` is axis-aligned; `//paste` lands at the player |
| Vanilla `.nbt` structure export | WorldEdit writes `.schem`, never `.nbt` |

### Mods added to `pack-two`

| Mod | Source | Sides |
|---|---|---|
| WorldEdit 7.2.15 | `packwiz cf add worldedit`, pinned to CurseForge file `4586218` | server + client |
| `d7oomtool` | new — `mods/d7oomtool/` in this repo, built by CI | server + client |

WorldEdit 7.2.15 (June 2023) is the **last Forge build EngineHub ever shipped**; everything after
it is NeoForge/Fabric only. Since 1.20.1 is itself frozen, being pinned to a 2023 release costs
nothing and there will never be an upgrade to chase.

WorldEdit's own `wand-item` stays as the vanilla wooden axe. `d7oomtool:d7oom_axe` is a different
item and handles its own clicks, so the two never double-handle an interaction.

---

## 2. The item

- **ID:** `d7oomtool:d7oom_axe`
- **Display name:** `d7oom's-tool` (lang key `item.d7oomtool.d7oom_axe` — the apostrophe lives in
  the translation, not the registry ID, which cannot contain one)
- **Model:** the vanilla `item/wooden_axe` texture, with a **permanent enchantment glint**
  (`Item#isFoil` returns `true` unconditionally — no real enchantment, so it survives a grindstone)
- **Rarity:** `EPIC`, so the name renders light purple
- Extends `Item`, **not** `AxeItem` — it does not mine, strip logs, or take damage. It is a wand
  that happens to look like an axe.

**Obtaining it.** No crafting recipe, no loot-table entry, no creative tab, no JEI entry. The only
route is `/give <player> d7oomtool:d7oom_axe`, and `/give` is already permission level 2. That is
the whole OP gate on acquisition — no extra code.

**Holding it without OP.** Every click handler and every `/d7` subcommand independently checks
`ServerPlayer#hasPermissions(2)`. A non-OP who somehow ends up holding one is holding an inert
decorative axe. Never trust that acquisition was the only gate.

---

## 3. Controls

| Input (wand in hand) | Effect |
|---|---|
| Right-click | Set a corner. First click sets `pos1`, second sets `pos2`, third starts a new selection. |
| Left-click a block | Set `pos1`. (WorldEdit muscle memory, kept.) |
| Shift + right-click | Clear the selection and exit any active mode. |

Between the first and second click, a ghost of the region tracks your crosshair, live. After the
second click the region locks and is pushed into WorldEdit.

Range is capped at `maxReach` (default 128 blocks). A click beyond it is ignored with a chat
message rather than clamped — a silently clamped corner is a wrong corner.

---

## 4. Commands

### WorldEdit's, used directly

Once a selection is locked, these already work, already tab-complete every block in all 387 mods,
already require OP, and already have undo:

```
//set create:andesite_casing        //replace stone deepslate
//copy    //paste    //undo    //redo
//schem save mybase                 //schem load mybase
```

### Ours, under `/d7`

`/d7` is used instead of `//` so we can never collide with WorldEdit's command namespace.

| Command | Purpose |
|---|---|
| `/d7 move [-e]` | Drag-move. The ghost becomes the actual blocks of the selection and follows the crosshair; right-click drops it. `-e` also moves entities. |
| `/d7 clone [-e]` | Same, but the original stays in place. |
| `/d7 mat <block>` | Sets the material the fill-ghost previews in. |
| `/d7 nbt <name> [-e]` | Exports the selection as a **vanilla `.nbt` structure** to `world/generated/d7oomtool/structures/<name>.nbt`, so `/place template d7oomtool:<name>` pastes it back with no mod involved. |
| `/d7 cancel` | Clears the selection and any active mode. |

**Ghost material.** For a fill, the block isn't known until you type `//set`. So the ghost renders
in the **last block you `//set`**, defaulting to `minecraft:stone`. This is captured by listening
to Forge's `CommandEvent` and parsing the argument of any `//set` a player runs — repeat fills are
therefore WYSIWYG with no extra input. `/d7 mat` overrides it explicitly.

**What travels on a move or clone.** Block entity NBT — chest contents, sign text, Create machine
state, spawner data — **always** moves. Entities (mobs, item frames, armor stands, paintings) move
**only** with `-e`. This matches WorldEdit's own default and means a cow that wandered into your
selection doesn't get teleported along with the base.

---

## 5. Components

Five units, each with one job and a testable boundary.

### `D7oomAxeItem` (both sides)
The item, as described in §2. Its `use` / `onLeftClickBlock` handlers do nothing but validate
permission and reach, then hand the hit position to `SelectionStore`.

### `SelectionStore` — server (authoritative)
Per-player: `pos1`, `pos2`, `mode` (`IDLE` / `SELECTING` / `MOVE` / `CLONE`), preview material,
and — in move/clone mode — the clipboard. Keyed by player UUID, cleared on logout. This is the
only source of truth; the client's copy is a mirror and is never trusted for an edit.

### `SelectionStore` — client (mirror)
Receives the server's state over one S2C packet. Adds one field the server never sees: the **live
corner**, raytraced from the crosshair every frame while `mode == SELECTING`. That field is why the
hologram costs the server nothing.

### `GhostRenderer` (client)
See §6.

### `WorldEditBridge` (server)
The **only** class that touches WorldEdit. Two mechanisms:

1. **Command dispatch** for selection: runs `//pos1 x,y,z` and `//pos2 x,y,z` as the player through
   `server.getCommands().performPrefixedCommand(...)`. No API coupling — if WorldEdit's internals
   change, nothing here breaks at compile time.
2. **`worldedit-core` API** for move/clone, because command dispatch genuinely cannot express
   "paste at exactly 104, 71, -230": `//move` is axis-aligned and `//paste` lands at the player.
   The bridge builds a `BlockArrayClipboard` with `ForwardExtentCopy`, then
   `new ClipboardHolder(clipboard).createPaste(editSession).to(BlockVector3.at(x, y, z))`.

   A move is a paste *and* a set-to-air of the source region **in one `EditSession`**, so `//undo`
   reverses the whole move in a single step rather than leaving a hole behind.

   Every `EditSession` the bridge creates is handed to `LocalSession#remember`, so **`//undo` works
   on our edits exactly as it does on WorldEdit's own.**

   The WorldEdit `Actor` is resolved by matching the player's UUID against
   `platform.getConnectedUsers()`. This deliberately avoids the `worldedit-forge` artifact, which is
   not published to Maven — only `worldedit-core` is.

**If WorldEdit is missing**, the bridge detects it once at server start (class-presence check) and
every `/d7` command replies with a clear chat error. It never throws and never crashes the server.

---

## 6. The ghost renderer

Ghost blocks are built into a cached `VertexBuffer`, rebuilt **only when the region or material
changes** — not per frame. It draws in one call on `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`
with depth-write disabled and alpha ≈ 0.45. A region of a few thousand blocks rebuilds in
milliseconds and then costs nothing at all while you pan the camera.

Alongside the ghost, the region's outline is drawn with `LevelRenderer.renderLineBox`, and the
volume (`w × h × d = N blocks`) is shown above the crosshair.

**The cap.** Ghost geometry is bounded by `previewMaxVolume` (config, default **32768** = 32³).
Above it the preview **turns off** and chat says once:

```
Region is 141,000 blocks — too big to preview (cap 32,768). The edit will still work.
```

The cap applies to the *preview only*. The edit itself is uncapped — WorldEdit handles any size.

---

## 7. Networking

Two packets on one Forge `SimpleChannel`:

| Packet | Direction | Payload |
|---|---|---|
| `SelectionSyncPacket` | S2C | `pos1`, `pos2`, `mode`, preview material, clipboard bounds |
| `CommitPacket` | C2S | The block position the client's ghost is anchored at, on a right-click |

`CommitPacket` exists because the client's raytrace is the one the player actually *sees*, and a
server-side re-raytrace can land on a different block. The server **validates** it — permission
level 2, within `maxReach` of the player, inside a loaded chunk — and rejects it otherwise. The
client proposes; the server disposes.

---

## 8. Failure modes

All of these are chat messages. None throw.

| Condition | Behaviour |
|---|---|
| WorldEdit not installed | `/d7` explains; the item still selects (harmless) |
| No selection yet | Command explains |
| Region crosses an unloaded chunk | **Refuse before editing** — a half-applied edit across a chunk border is far worse than no edit |
| Region exceeds `previewMaxVolume` | Preview off, edit still allowed (§6) |
| Click beyond `maxReach` | Ignored, with a message |
| Non-OP interaction | Silently inert |

---

## 9. Configuration

`config/d7oomtool-common.toml`:

| Key | Default | Meaning |
|---|---|---|
| `previewMaxVolume` | `32768` | Ghost-block cap (§6) |
| `maxReach` | `128` | How far a corner can be placed |
| `opLevel` | `2` | Permission level for the item and all `/d7` commands |

---

## 10. Build and distribution

Source lives at `mods/d7oomtool/` in this repo — a ForgeGradle 6 project (MC 1.20.1, Forge 47.4.x,
official mappings), with `maven.enginehub.org` added for a **`compileOnly`** dependency on
`com.sk89q.worldedit:worldedit-core:7.2.15`. `compileOnly` because the running WorldEdit jar
provides it — we must never shade it.

The release job in `.github/workflows/`:

1. Builds the jar.
2. Attaches it to the GitHub Release for the tag.
3. Computes its SHA-512, rewrites `pack-two/mods/d7oomtool.pw.toml` to point at the release asset
   URL with that hash, and runs `packwiz refresh`.
4. Commits the refreshed index.

Step 3 is not optional: the hash changes on every build, and CI already enforces that the packwiz
index is in sync. Players get the mod on their next `update.bat`; the server picks it up on its
next restart, through the packwiz-installer path that already exists.

---

## 11. Testing

| Unit | How |
|---|---|
| `SelectionStore` | Unit tests — corner ordering, normalization to min/max, mode transitions, logout clearing |
| `/d7 nbt` export | GameTest — write a known structure, export, `/place template` it back, assert blocks and NBT match |
| `WorldEditBridge` | GameTest — copy/paste/move round-trips; block entity NBT survives; entities move only with `-e`; `//undo` reverses a move in one step |
| Missing-WorldEdit path | Unit test — bridge no-ops and reports, never throws |
| Permission gate | GameTest — a level-0 player's clicks and `/d7` calls change nothing |
| `GhostRenderer` | **Manual, in-game.** There is no honest way to assert on pixels. Checklist: ghost tracks crosshair; matches the block `//set` actually places; disappears above the cap; no FPS drop at the cap; no z-fighting with real blocks |

---

## 12. Build order

Each phase leaves the pack in a working, playable state.

1. Mod skeleton, item, `/give`, OP gate. *(Ships a useless glowing axe — but a real, installable one.)*
2. Selection + sync packet + wireframe outline only.
3. Ghost renderer + volume cap.
4. `WorldEditBridge` selection push → `//set` works end to end. **This is the first genuinely useful build.**
5. `/d7 move` and `/d7 clone` with the drag ghost.
6. `/d7 nbt`.
7. CI wiring + packwiz pins.

Phases 1–4 deliver the headline feature (right-click, right-click, `//set`, with a hologram).
Phases 5–6 are the rest.
