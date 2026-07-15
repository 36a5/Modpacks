# Progression, boss XP, and shadow scaling

**Date:** 2026-07-14
**Status:** approved, not yet implemented
**Extends:** `2026-07-14-boss-progression-design.md` (the per-boss final-HP model)

Four changes, one release, all in the `shababparty` mod. Together they turn boss-hunting into the
progression spine: fight a dimension's ladder, gain levels and gear, move to the next, and eventually
be strong enough for the Ender Dragon.

1. Lower the boss curve's floor to ×10 and reflow it.
2. Killing a boss grants Solo Leveling **levels** to the whole party, ramping per boss.
3. Triple the Solo Leveling XP rate (gamerule 10 → 30).
4. Shadow soldiers scale with their Monarch's level and grow as he levels.

---

## 1. Boss HP/damage curve — floor lowered to ×10

The unlisted-boss fallback drops from ×30 to **×10** health (Wither ≈ 3,000, Warden ≈ 5,000), and the
laddered targets reflow to ramp up from that floor. The steep endgame is unchanged: the Ender Dragon
is still 150,000, above everything.

Health is the final total; damage is the multiplier on everything the boss deals.

**Overworld & Nether — the lowest tier, and it must come DOWN, not up.** These are wandering
minibosses a player meets by accident, not dungeon bosses they seek out, so at the old ×30 they were
absurd — the Mutant Zombie ("the hulk", 150 base) was a **4,500 HP** random encounter, Frostmaw ("the
ice yeti", 250 base) was **7,500**. They get explicit low targets instead, tuned to be beatable in
early-to-mid overworld gear. These are the entry rung of the whole game.

| Overworld / Nether | HP | Dmg | Levels |
|---|---|---|---|
| mutantmonsters:mutant_zombie | 1,200 | 3× | 4 |
| mutantmonsters:mutant_skeleton | 1,000 | 3× | 3 |
| mutantmonsters:mutant_creeper | 800 | 3× | 3 |
| mutantmonsters:mutant_enderman | 1,500 | 4× | 5 |
| mowziesmobs:frostmaw | 1,500 | 3× | 5 |
| mowziesmobs:ferrous_wroughtnaut | 1,200 | 3× | 4 |
| mowziesmobs:umvuthi | 1,400 | 3× | 4 |
| mowziesmobs:sculptor | 1,600 | 3× | 5 |
| conjurer_illager:conjurer | 900 | 3× | 3 |
| illagerinvasion:invoker | 800 | 3× | 3 |
| deeperdarker:stalker | 1,500 | 4× | 5 |
| born_in_chaos_v1:sir_pumpkinhead | 1,200 | 3× | 4 |
| born_in_chaos_v1:lord_pumpkinhead | 2,000 | 4× | 6 |
| born_in_chaos_v1:fallen_chaos_knight | 1,000 | 3× | 3 |
| born_in_chaos_v1:nightmare_stalker | 1,000 | 3× | 3 |
| minecraft:elder_guardian | 1,500 | 3× | 4 |
| minecraft:wither | 4,000 | 4× | 6 |
| minecraft:warden | 6,000 | 5× | 8 |

The Wither and Warden are kept meaningfully tankier than the minibosses — they are real fights and the
top of the overworld tier, the bridge into the dimensions.

**Anything not named anywhere** still falls back to a flat ×10 HP / ×5 damage as a safety net, so a
boss from a mod added later is scaled sanely without a code change.

| Twilight Forest | HP | Dmg | | Aether | HP | Dmg |
|---|---|---|---|---|---|---|
| naga | 2,000 | 3× | | slider | 22,000 | 8× |
| lich | 3,500 | 4× | | valkyrie_queen | 32,000 | 11× |
| minoshroom | 5,500 | 5× | | sun_spirit | 42,000 | 14× |
| hydra | 8,000 | 6× | | | | |
| knight_phantom | 11,000 | 7× | | **Undergarden** | | |
| ur_ghast | 15,000 | 8× | | forgotten_guardian | 16,000 | 8× |
| alpha_yeti | 20,000 | 9× | | **Deep Aether** | | |
| snow_queen | 26,000 | 10× | | eots_controller | 45,000 | 13× |

| Cataclysm | HP | Dmg | | Blue Skies | HP | Dmg |
|---|---|---|---|---|---|---|
| netherite_monstrosity | 12,000 | 8× | | summoner | 20,000 | 8× |
| ender_guardian | 16,000 | 9× | | starlit_crusher | 24,000 | 9× |
| ignis | 22,000 | 11× | | alchemist | 28,000 | 10× |
| maledictus | 22,000 | 11× | | arachnarch | 32,000 | 11× |
| the_harbinger | 32,000 | 13× | | **Bosses'Rise** | | |
| scylla | 32,000 | 13× | | yeti | 16,000 | 8× |
| the_leviathan | 48,000 | 16× | | sandworm | 24,000 | 11× |
| ancient_remnant | 75,000 | 20× | | underworld_knight | 36,000 | 14× |
| **Lost Aether** | | | | infernal_dragon | 55,000 | 16× |
| aerwhale_king | 52,000 | 14× | | kraken | 55,000 | 16× |

| Endgame | HP | Dmg |
|---|---|---|
| **minecraft:ender_dragon** | **150,000** | **30×** |

Mechanics are exactly the progression spec: fixed-UUID `ADDITION` modifier of `target − baseValue`,
idempotent across chunk reloads, health topped up on first application only, damage on
`LivingHurtEvent` credited to the owner. AttributeFix stays a hard dependency (vanilla caps
`max_health` at 1024).

---

## 2. Boss kills grant Solo Leveling levels — party-shared, ramping

When a boss dies, every party member within `xpShareRadius` (the existing config, default 64) of the
kill — killer included — is granted a number of Solo Leveling **levels**. The count ramps up the
progression, so later bosses are worth more.

### How levels are granted

Solo Leveling's level-up is `LevelUpProcedure.onPlayerTick`: each tick, if `PlayerVariables.Xp >=
MaxXP`, it subtracts `MaxXP`, does `Level++`, and grants that level's stat points (Vitality,
Strength, Intelligence, perception, Speed, Durability) and rank. **That stat grant is why we do not
touch `Level` directly** — bumping the field would skip the stats and leave a hollow level.

So to award N levels we add `N × MaxXP` to `Xp` and let the mod's own tick consume it, one correct
level-up per tick. This reuses the mod's entire level-up path — stats, rank, the "Leveled Up" title,
the sound — and cannot desync its derived values. It lands approximately N levels (exact if `MaxXP`
were constant; it drifts slightly as the threshold grows, which for a reward is immaterial).

`MaxXP` and `Xp` are read and written through the `sololeveling:player_variables` capability, the same
one `PartySupport` already uses, followed by `syncPlayerVariables`.

### The per-boss level table

Unlisted bosses grant a flat `bossBaseLevels` (default 3). Laddered bosses:

| Boss | Levels | | Boss | Levels |
|---|---|---|---|---|
| tf:naga | 10 | | aether:slider | 30 |
| tf:lich | 12 | | aether:valkyrie_queen | 38 |
| tf:minoshroom | 15 | | aether:sun_spirit | 45 |
| tf:hydra | 18 | | cataclysm:netherite_monstrosity | 20 |
| tf:knight_phantom | 22 | | cataclysm:ancient_remnant | 60 |
| tf:ur_ghast | 26 | | bosses'rise:kraken | 45 |
| tf:alpha_yeti | 30 | | deep_aether:eots_controller | 50 |
| tf:snow_queen | 35 | | **minecraft:ender_dragon** | **100** |

(Bosses between the named rungs interpolate; the full table ships in config.) Naga at +10 is the
entry, the Dragon at +100 is the payoff. With the ×3 rate in §3 on top of normal mob XP, boss-hunting
becomes the fastest way to level.

The grant is stored per-boss in the same tier list as HP and damage:
`"entity_id=targetHealth,damageMultiplier,levels"`.

### Recipient resolution

Reuses `PartySupport`'s logic: the killer is the player, or the owner of a tamed pet (a shadow) that
landed the kill. Party members are found through `FTBTeamsAPI`, filtered to the party team, online,
same dimension, within radius. Solo Leveling's own per-kill XP still flows separately through
`PartySupport`; this is an additional, larger, boss-only reward.

---

## 3. Triple the Solo Leveling XP rate

The `soloLevelingXPMultiplier` gamerule is **10** in the live world. It becomes **30**.

Set from the `slb-jobs` datapack's `load.mcfunction`, which already runs on every load and reload, so
it applies on the next server start and needs no manual command:

```mcfunction
gamerule soloLevelingXPMultiplier 30
```

This multiplies all Solo Leveling XP — normal mob kills and the boss level-grants alike — so the two
compound.

---

## 4. Shadow soldiers scale with their Monarch's level

Solo Leveling's shadow soldiers are static: a level-200 Shadow Monarch summons the same Igris a
level-40 one does. They should grow with their owner.

### Which entities

The mod ships a `minecraft:shadows` entity-type tag with exactly the 13 shadow soldiers
(`igris_shadow`, `beru_shadow`, `kamish_shadow`, the orcs, `steel_fang_wolf_shadow`,
`shadow_polar_bear`, `shadow_sold_1`, and the goblins). This tag is the selector — it excludes the
mod's other `TamableAnimal`s that are not soldiers (flame vortexes, bear traps, banners).

### The scaling

For a shadow whose owner is a player, read the owner's `PlayerVariables.Level` and apply:

- **health** × `(1 + Level × 0.10)` — level 100 = 11×, level 200 = 21×
- **attack damage** × `(1 + Level × 0.05)` — level 100 = 6×, level 200 = 11×

via fixed-UUID modifiers on `MAX_HEALTH` and `ATTACK_DAMAGE`.

### Growing as the owner levels

Applied on `EntityJoinLevelEvent` for freshly summoned shadows, and **re-evaluated on a periodic
sweep** (every 100 ticks) over loaded shadows: the sweep reads the owner's current level, and if it
implies a different multiplier than the shadow currently carries, it replaces the modifier. So a
Monarch who levels up sees his standing army get stronger within five seconds, not only his next
summons. Health is topped up to the new maximum when the multiplier increases.

A shadow with no player owner (unowned, or owner offline/unresolved) is left at vanilla stats until
its owner is present again — no crash, no guess.

---

## Config (additions to `[bossScaling]`, plus new blocks)

```toml
[bossScaling]
  enabled = true
  healthMultiplier = 10.0    # overworld floor, was 30
  damageMultiplier = 5.0
  # "entity_id=targetHealth,damageMultiplier,levelReward"
  bossTiers = [ "twilightforest:naga=2000,3,10", ... , "minecraft:ender_dragon=150000,30,100" ]
  exclusions = ["sololeveling"]      # Naga and Lich removed - they are the TF entry rung now

[bossLevels]
  enabled = true
  bossBaseLevels = 3         # unlisted bosses

[shadowScaling]
  enabled = true
  healthPerLevel = 0.10
  damagePerLevel = 0.05
  resyncIntervalTicks = 100
```

`enderDragonHealthMultiplier` / `enderDragonDamageMultiplier` are **removed** — the dragon is a tier
entry. `minecraft:ender_dragon` is added to the `scaled_bosses` tag. `healthMultiplier` changes 30 → 10.

Because Forge keeps existing values in an existing config, the **live server config must be edited by
hand** for `healthMultiplier` and the new `bossTiers`; the new `[bossLevels]` and `[shadowScaling]`
blocks are added fresh with their defaults automatically.

---

## Verification

- **Curve:** `/summon twilightforest:knight_phantom` (11,000) is tankier than `/summon
  twilightforest:hydra` (8,000), despite Knight Phantom's lower base HP. Wither ≈ 3,000 (×10 floor).
- **Boss levels:** in a party, kill a summoned Naga near a partied member; both gain ~10 Solo
  Leveling levels (watch the panel, `;`), with the stat points that come with them. Kill it again at
  a much higher level — still ~10, because the grant tracks `MaxXP`.
- **XP rate:** `/gamerule soloLevelingXPMultiplier` reads 30 after a reload.
- **Shadows:** as a low-level Monarch, summon Igris and note its health. Raise your Level (`/slr` or
  play), wait five seconds — the same Igris is now tankier. A fresh summon is stronger still.
- **No double-scaling:** a shadow reloaded across a chunk boundary keeps one modifier, not a stack.
