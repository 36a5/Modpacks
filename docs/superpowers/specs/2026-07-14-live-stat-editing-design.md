# Live stat editing — design

**Status:** approved, not yet implemented
**Target pack:** `pack-two` (Shabab 2) — Minecraft 1.20.1 / Forge 47.4.x
**Date:** 2026-07-14

An OP-only `/stat` command that sets any of the six Discord-leaderboard numbers for any player —
online or offline — **while the server is running**, with no bot change and no downtime.

---

## 1. The problem

The `!leaderboard` board ranks players by lifetime totals read straight out of
`server/run/world/stats/<uuid>.json`. Those files cannot be edited by hand on a live server: the
server holds each player's stats in an in-memory `ServerStatsCounter` and rewrites the file *from
memory* on autosave and on logout, so any hand edit is silently overwritten.

Today the only fix is `server/set-player-deaths.ps1`, which refuses to run unless the server is
stopped, and covers exactly one of the six columns.

Two things that look like solutions and are not:

- **Setting a stat-criterion scoreboard.** `scoreboard objectives add d minecraft.custom:minecraft.deaths`
  then `/scoreboard players set <p> d 5` does nothing useful. Stat → scoreboard is one-way; the
  score is clobbered on the next stat update and the JSON is never touched.
- **Mirroring the six columns into dummy scoreboards** (the `pk_total` pattern from `gate_kills`).
  That makes the *scoreboard* editable, but the board reads JSON, so it would need a bot rewrite —
  and it creates a second source of truth that drifts from the file the game actually maintains.

## 2. Approach

Edit the object the server itself flushes, not the file it flushes over.

The command lives in the existing house mod **`shababparty`** (1.2.0 → 1.3.0) rather than a new jar:
`tools/shababparty/build.sh` already resolves every SRG / Forge / brigadier jar needed to compile
against, and already installs the result into `pack-two/mods/` and `server/run/mods/`. A second mod
would duplicate that wiring and add a jar to keep in sync forever.

Write path, for one `<player> <field> <value>`:

1. **Resolve the UUID.** Online → the live `ServerPlayer`. Offline → the server's profile cache
   (`usercache.json`), the same source `set-player-deaths.ps1` reads today.
2. **Get the server's own counter.** Reach `PlayerList`'s private `uuid → ServerStatsCounter` map
   through an accessor mixin (the mod already mixins into `ClientTeamManagerImpl` and
   `TamableAnimal`, so this is house style). If the player is absent from the map, construct their
   counter from disk and **insert it into the map** — the server thereby adopts the edited counter
   as its own, which is what makes the edit survive.
3. **`setValue(...)`**, then **`save()`** — the counter writes `world/stats/<uuid>.json` at once.
4. If the player is online, push the update to their client so their in-game stats screen agrees.

Because the server's live copy *is* the copy we edited, the next autosave re-writes the same
numbers instead of reverting them. The Discord bot keeps reading the JSON it always read and needs
no change; the new value appears on its next refresh (`!board`).

## 3. The command

```
/stat get <player> <field>
/stat set <player> <field> <value>
/stat add <player> <field> <amount>
```

Permission level 3 (OP). Usable from chat, the server console, and RCON — so the bot can drive it
later if that is ever wanted. `<player>` accepts any name in the profile cache, not just online
players; unknown names are a clean error, not a silent no-op.

The six fields map 1:1 onto the board's columns:

| `<field>` | Written to |
|---|---|
| `deaths` | `minecraft:custom` → `minecraft:deaths` |
| `mob_kills` | `minecraft:custom` → `minecraft:mob_kills` |
| `player_kills` | `minecraft:custom` → `minecraft:player_kills` |
| `play_time` | `minecraft:custom` → `minecraft:play_time` |
| `blocks_mined` | sum of `minecraft:mined`; delta absorbed by `minecraft:stone` |
| `blocks_placed` | sum of `minecraft:used`; delta absorbed by `minecraft:dirt` |

`play_time` is in **ticks** (the unit the game stores; 20 ticks = 1 second). The command echoes the
equivalent in hours so a wrong order of magnitude is obvious.

`set` rejects a negative value. `add` accepts a negative amount — that is how you subtract — but
refuses if it would take the field below 0. No field can end up negative by any route.

### The two sum fields

`blocks_mined` and `blocks_placed` are not scalars. In a real stats file, `minecraft:mined` holds
80 per-block entries summing to 3,060 and `minecraft:used` holds 97 entries summing to 9,874. The
board shows the sum, so there is no single field to write.

`set <p> blocks_mined 5000` therefore: sums every entry, computes `5000 − 3060 = 1940`, and adds
that to the `minecraft:stone` entry. The sum lands exactly on 5000 regardless of how the bot
aggregates. The sum is taken over the **whole block registry, modded blocks included**, matching
the bot, which sums every key under `minecraft:mined` whatever its namespace. `blocks_placed` works
the same over the item registry, sinking into `minecraft:dirt`.

Both sinks are ordinary blocks a player plausibly mines and places, so an inflated entry never
looks anomalous.

**Lowering below the floor is refused.** The sink entry cannot go negative, so the lowest reachable
total is `sum − sink`. Asking for less than that would require zeroing other block entries and
destroying per-block detail, so the command errors and names the floor instead:

```
blocks_mined cannot go below 2,847 without wiping other block entries
(minecraft:stone holds only 213 of the 3,060 total).
```

## 4. Scope of change

| File | Change |
|---|---|
| `tools/shababparty/src/dev/alshabab/shababparty/StatsCommand.java` | new — the command |
| `tools/shababparty/src/dev/alshabab/shababparty/mixin/PlayerListAccessor.java` | new — reach the `uuid → ServerStatsCounter` map |
| `tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java` | register the command on `RegisterCommandsEvent` |
| `tools/shababparty/res/mixins.shababparty.json` | list the new accessor |
| `tools/shababparty/build.sh` | `VERSION=1.2.0` → `1.3.0` |
| `pack-two/mods/shababparty-1.2.0.jar` | replaced by `-1.3.0.jar`; then `packwiz refresh` in `pack-two/` |
| `server/set-player-deaths.ps1` | **deleted** — same job, but needs downtime and covers one column |
| `docs/admin-commands.md` | replace "Editing a player's death count" with the live command |
| `docs/guides/systems.md` | Leaderboards section: numbers are still unfakeable by players, but an admin can now correct them live |
| `docs/CHANGELOG.md` | entry |

## 5. Implementation risk

`build.sh` compiles against production-mapped jars, so **every Minecraft symbol must be written in
SRG form** (`m_9236_`, `f_12988_`) — there is no reobfuscation step to translate readable names.
Guessing these is the likeliest way to lose an afternoon.

So the first implementation step is to resolve each one with `javap` against the server's own
`server-1.20.1-*-srg.jar`, and write them into a table in the plan before any code is written:
`PlayerList.stats`, `ServerStatsCounter` (constructor, `setValue`, `save`, `sendStats`),
`StatsCounter.getValue`, `Stats.CUSTOM` / `BLOCK_MINED` / `ITEM_USED`, `Stats.DEATHS` /
`MOB_KILLS` / `PLAYER_KILLS` / `PLAY_TIME`, `StatType.get`, `BuiltInRegistries.BLOCK` / `ITEM`,
`MinecraftServer.getProfileCache`, and `CommandSourceStack.hasPermission`.

A related unknown to confirm the same way: `ServerStatsCounter.setValue(Player, Stat, int)` takes a
`Player` it appears never to read (the base implementation just puts to a map). If that holds, an
offline edit can pass `null`. If it does not, the offline path constructs the counter and writes
through it directly instead.

## 6. Verification

The server is down, so this can be tested end to end before anyone plays.

1. Build; `packwiz refresh` in `pack-two/`; boot the server against the local pack
   (`$env:PACK_URL`, per `docs/mod-workflow.md`).
2. **Online:** `/stat set <me> deaths 5`, then read `world/stats/<uuid>.json` **while the server is
   still up** — it must already say 5. Play a minute, force an autosave, read again: still 5. This
   is the exact case the old script could not do.
3. **Offline:** stop nothing; pick a player who is not logged in, `/stat set <them> mob_kills 100`,
   read their JSON. Then have them log in and back out, and confirm the server did not revert it.
4. **Sum fields:** `/stat set <me> blocks_mined 5000`, then confirm the entries under
   `minecraft:mined` sum to exactly 5000.
5. **Floor:** ask for a `blocks_mined` below `sum − stone` and confirm it errors with the floor
   named, and that the file is unchanged.
6. **Board:** `!board` in Discord and confirm the new numbers render.
