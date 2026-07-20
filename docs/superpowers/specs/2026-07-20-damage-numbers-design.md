# Floating damage numbers — design

**Status:** approved, not yet implemented
**Target pack:** `pack-two` (Shabab 2) — Minecraft 1.20.1 / Forge 47.4.x
**Date:** 2026-07-20

In-world damage popups in `shababparty`, split into three independently coloured and independently
hideable buckets, with a master show/hide keybind and a hand-rolled config screen on NUMPAD 6.

The purpose is weapon comparison. The pack has 391 mods and several dozen viable weapons whose
listed tooltip damage says little about what actually lands after armour, Cataclysm's
armour-negation, Epic Fight modifiers and Solo Leveling's scaling. Seeing the real number in the
fight settles it.

---

## 1. The problem

Damage is computed **server-side**. The client is told an entity's new health, never how much was
dealt or by whom. Any client-only approach — diffing `LivingEntity` health on `ClientTickEvent`,
hooking a client mixin on `hurt` — reconstructs a number that is wrong whenever absorption, armour,
resistance, or a mod's damage hook is involved. Which, in this pack, is most hits.

So the server has to compute and tell the client.

A second problem is that "damage" is two numbers, and the interesting comparison needs both: what
the weapon rolled, and what actually came off the target's health bar. A weapon that rolls 24 into
a heavily armoured boss for 9 is a different weapon from one that rolls 11 for 9.

## 2. Approach

Two Forge events, paired within a tick, then one packet to exactly one player.

- **`LivingHurtEvent`** fires first, carrying the raw incoming amount before armour and enchantment
  reduction. Stash it keyed by victim entity id.
- **`LivingDamageEvent`** fires after all reduction, carrying the final amount that will leave the
  health bar. It consumes the stash and emits the packet.

Pairing through a stash rather than reading one event twice is necessary because neither event alone
has both numbers, and Forge exposes no combined event. The stash is a small `Int2FloatMap`-shaped
`HashMap<Integer, Float>` cleared on consumption, with a defensive clear on `ServerTickEvent` so a
cancelled `LivingDamageEvent` cannot leak an entry forever.

Bucketing is decided server-side from attacker and victim, because only the server knows both:

| Bucket | Condition | Packet recipient | Default colour |
|---|---|---|---|
| `OUTGOING` | attacker is a `ServerPlayer` | that attacker | Yellow `#FFFF55` |
| `MOB_TO_YOU` | victim is a `ServerPlayer`, attacker is not a player | that victim | Red `#FF5555` |
| `PLAYER_TO_YOU` | victim is a `ServerPlayer`, attacker is a player | that victim | Purple `#FF55FF` |

Every packet therefore targets exactly one player. Mob-versus-mob damage produces no packet at all.
This keeps bandwidth proportional to the damage *you* are involved in, and means one player's
numbers are never visible to another.

A single hit where a player damages another player produces two packets — `OUTGOING` to the
attacker, `PLAYER_TO_YOU` to the victim — which is correct: each sees their own side of it.

### Rejected alternatives

- **Server-spawned `text_display` entities.** Works with no client code and no packets, and renders
  in vanilla. Rejected because display entities are shared world state: every player near the fight
  sees every number, colours cannot differ per viewer, and the keybind and config screen — the
  actual request — are impossible.
- **Client-side health diffing.** No packets, no server code. Rejected: produces wrong numbers under
  absorption and armour, cannot distinguish attacker, and silently disagrees with reality in exactly
  the boss fights the feature exists for.

## 3. Build change

`tools/shababparty/build.sh` compiles against `server-1.20.1-...-srg.jar` — vanilla in SRG names,
server side only. It contains no `net.minecraft.client` classes, so today nothing client-side can be
compiled at all. The existing `ClientTeamManagerImplMixin` is not a counter-example: it targets FTB
Teams' own classes, which come from a mod jar.

The joined SRG jar — vanilla client *and* server in SRG names, 1724 client classes including
`Minecraft`, `KeyMapping` and `GuiGraphics` — exists on disk in the minecolonies-fork ForgeGradle
cache at:

```
minecolonies-fork/build/fg_cache/mcp/1.20.1-20230612.114412/joined/
  52a985215b1b4ac60955bf4c0590228df839003d/rename/output.jar
```

Copy it once to `tools/shababparty/libs/minecraft-1.20.1-joined-srg.jar` and add it to `DEPS`,
replacing `MC_SRG`. Vendoring rather than referencing keeps the build self-contained: a
`gradle clean` in minecolonies-fork, or deleting that fork entirely, must not break shababparty.

Everything else is already resolved by the current `DEPS`. Verified present in
`forge-1.20.1-47.4.18-universal.jar`: `RegisterKeyMappingsEvent`, `RenderLevelStageEvent`,
`LivingHurtEvent`, `SimpleChannel`, `ConfigScreenHandler`.

Version bumps `1.20.0` → `1.21.0`.

## 4. Components

Seven new files. The existing `ShababParty.java` is already 515 lines with a large nested `Config`
class; none of the new configuration goes into it.

```
network/Net.java                  SimpleChannel registration, protocol version
network/DamageNumberPacket.java   entityId, raw, finalAmount, bucket ordinal
DamageNumbers.java                server: the two hooks, the stash, bucketing
client/ClientConfig.java          ForgeConfigSpec Type.CLIENT
client/DamageNumberKeys.java      the two KeyMappings
client/ClientDamageNumbers.java   popup list, tick/cull, world render
client/DamageNumbersScreen.java   the config Screen
```

Everything under `client/` is registered through `@Mod.EventBusSubscriber(value = Dist.CLIENT)` and
reached from common code only via `DistExecutor`, so a dedicated server never classloads a client
type. This matters: shababparty ships to `server/run/mods/` as well as `pack-two/mods/`, and a stray
client reference in a common path crashes the server on boot.

**Config is `ModConfig.Type.CLIENT`**, not `COMMON` like the existing spec. These are per-player
display preferences. They belong in the client's config directory, and must not be something the
server dictates or that two players on one server have to agree on.

## 5. Rendering

`ClientDamageNumbers` holds a list of live popups, each a position, a formatted string, a colour, and
a spawn time. `ClientTickEvent` advances and culls; `RenderLevelStageEvent` at
`Stage.AFTER_PARTICLES` draws them.

Each popup spawns at the victim's eye position, drifts upward, and fades to fully transparent over
its lifetime. Text is billboarded — rotated to face the camera each frame using the camera's yaw and
pitch — so numbers stay legible from any angle.

Hits landing in the same tick receive a small deterministic horizontal jitter derived from the popup
index, so stacked numbers do not render exactly on top of each other. This is not cosmetic polish:
Cataclysm's Meat Shredder ignores invincibility frames and Epic Fight combos land 3–5 hits per swing,
so overlapping same-tick numbers are the normal case for the weapons most worth measuring.

The display format with both numbers enabled is `raw (final)` — e.g. `24 (9)`. With only one enabled,
just that number. Values render to one decimal place, trailing `.0` stripped.

The popup list is capped (default 64, configurable). At the cap the oldest is dropped. Without this,
a Meat Shredder held down in a crowd is an unbounded allocation.

## 6. Keybinds

Both are registered as real `KeyMapping`s under a `key.categories.shababparty` category, which is
what makes them appear in **Options → Controls** and rebindable there through vanilla UI. No custom
key handling, no conflict-detection code to write — Minecraft already does all of it.

| Default | GLFW | Action |
|---|---|---|
| NUMPAD 5 | `GLFW_KEY_KP_5` (325) | Master show/hide toggle |
| NUMPAD 6 | `GLFW_KEY_KP_6` (326) | Open the config screen |

The master toggle flips the runtime flag and writes it to the client config, so the state survives a
restart. It also prints a brief action-bar confirmation, because a keybind whose only feedback is the
absence of numbers in a quiet moment is indistinguishable from a keybind that did nothing.

## 7. Config screen

A hand-rolled `Screen`. Cloth Config is present in the pack and would be less code, but adding it to
`DEPS` turns a currently dependency-free build into one that breaks when a pack update moves Cloth's
version, and makes shababparty hard-require a mod it otherwise does not need.

Layout — a master toggle, then one row per bucket:

```
Damage you deal      [x]  ■ ■ ■ ■ ■   #FFFF55  ▐
Mobs → you           [x]  ■ ■ ■ ■ ■   #FF5555  ▐
Players → you        [x]  ■ ■ ■ ■ ■   #FF55FF  ▐
                          presets      hex      preview
```

Each row has an enable checkbox, five preset swatches, a hex field, and a live preview. Clicking a
swatch fills the hex field and updates the preview; the active swatch is outlined so the mapping of
bucket to colour is readable at a glance. Any hex is accepted; invalid input reverts to the previous
valid colour on focus loss rather than rejecting keystrokes, so partial typing is not fought.

Presets are Minecraft's own chat-colour palette, which is chosen for legibility over arbitrary world
backgrounds:

| Hex | Name |
|---|---|
| `#FF5555` | Red |
| `#FFFF55` | Yellow |
| `#55FF55` | Green |
| `#55FFFF` | Aqua |
| `#FF55FF` | Purple |

All three bucket defaults come from this set, so the common case needs no typing. Outgoing defaults
to yellow rather than another red-family colour because red and purple are the two that are hardest
to tell apart at distance in low light.

Below the rows: `show raw` and `show final` toggles, lifetime and scale sliders, and an **Edit
Keybinds** button that opens the vanilla controls screen. The screen is also registered through
`ConfigScreenHandler` so it opens from the Mods list.

## 8. Settings

All under `ModConfig.Type.CLIENT`, written to `config/shababparty-client.toml`.

| Key | Type | Default |
|---|---|---|
| `enabled` | boolean | `true` |
| `outgoingEnabled` | boolean | `true` |
| `outgoingColor` | string hex | `FFFF55` |
| `mobToYouEnabled` | boolean | `true` |
| `mobToYouColor` | string hex | `FF5555` |
| `playerToYouEnabled` | boolean | `true` |
| `playerToYouColor` | string hex | `FF55FF` |
| `showRaw` | boolean | `true` |
| `showFinal` | boolean | `true` |
| `lifetimeTicks` | int 5–100 | `20` |
| `scale` | double 0.25–4.0 | `1.0` |
| `riseSpeed` | double 0.0–1.0 | `0.35` |
| `maxPopups` | int 8–256 | `64` |

Colours are stored as bare six-digit hex without a leading `#` (`FFFF55`), because a `#` opens a
comment in TOML and would have to be quoted defensively on every hand edit. The config screen
displays and accepts the `#` form and strips it on write, so a pasted `#FFFF55` also works.

Per-bucket enable flags are honoured **client-side at render time**, not server-side at packet time.
Toggling a bucket off must not require a server round trip, and the cost of a packet for a hidden
bucket is negligible next to the latency of the alternative.

## 9. Error handling

- **Packet for an entity the client cannot see** (out of render distance, already removed): dropped
  silently. Normal, not an error.
- **Invalid hex in config file** (hand-edited): falls back to that bucket's default and logs once at
  warn. Never crashes the screen or the renderer.
- **`LivingDamageEvent` cancelled by another mod:** no packet, and the stashed raw value is cleared
  on the next server tick sweep.
- **Final damage of zero** (fully absorbed, immune): no packet. A stream of `0` popups from an immune
  boss phase is noise.
- **Server without shababparty, client with it:** no packets ever arrive, nothing renders, no crash.
  The channel is registered as optional on both sides.

## 10. Testing

Compilation is the first gate — the SRG-name build has no IDE and no mappings, so a typo in an
obfuscated method name is a compile error rather than a silent runtime failure. That is a feature
here and the reason to build early and often.

In-game verification, in `pack-two`:

1. Hit a passive mob with a plain sword — one yellow number, `raw (final)` both plausible.
2. Hit an armoured mob — raw and final visibly diverge.
3. Take a hit from a mob — red number.
4. Take a hit from another player — purple, and confirm the attacker sees yellow simultaneously.
5. NUMPAD 5 — all numbers stop, action-bar confirms, state survives a restart.
6. NUMPAD 6 — screen opens, swatches apply, hex accepts custom input, changes take effect live.
7. Disable one bucket — only that bucket stops.
8. Options → Controls — both binds listed under Shabab Party, rebinding works.
9. Meat Shredder against a crowd — numbers jitter apart rather than overlapping, no frame drop.
10. Dedicated server boot with the new jar — no `NoClassDefFoundError` from a client type.
