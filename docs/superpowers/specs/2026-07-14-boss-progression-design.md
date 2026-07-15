# Boss progression — a real difficulty curve, not a flat multiplier

**Date:** 2026-07-14
**Status:** approved, not yet implemented
**Supersedes:** the flat-multiplier model in `2026-07-14-boss-scaling-design.md`

The goal is a progression: players should have to upgrade gear and level up to beat each successive
dimension, and the Ender Dragon is the wall at the end. A flat ×30 does not do that, and — this is
the crux — **neither does an increasing multiplier**, because base health does not track difficulty.

## Why multipliers cannot express progression

Real base health, pulled from the mod jars:

| Twilight Forest (gate order) | base HP | | Cataclysm | base HP |
|---|---|---|---|---|
| Naga | 120 | | Netherite Monstrosity (early) | 600 |
| Lich | 100 | | Ender Guardian | 333 |
| Minoshroom | 120 | | Ignis | 450 |
| Hydra | 360 | | Ancient Remnant (**hardest**) | 450 |
| **Knight Phantom** | **35** | | Leviathan | 400 |
| Ur-Ghast | 250 | | | |
| Alpha Yeti | 200 | | | |
| Snow Queen | 200 | | | |

Knight Phantom is the fifth Twilight Forest boss and has 35 base HP — a tenth of the Hydra it comes
after. At an increasing multiplier (Hydra ×50 = 18,000; Knight Phantom ×80 = 2,800) the *later* boss
is *weaker*. Cataclysm is worse: Netherite Monstrosity, an early boss, has more base HP than Ancient
Remnant, the hardest one in the mod. A multiplier — flat or ramping — inherits this mess.

## The model: a per-boss final-HP target

Each boss in a ladder is given the **health it ends up at**, in progression order, and a **damage
multiplier**. Because the endpoint is set directly, each rung is guaranteed tankier than the last no
matter what the mod's base value was.

- **Listed bosses** get their target from the curve below.
- **Unlisted bosses** — the whole overworld, and anything not in a ladder — fall back to the flat
  `healthMultiplier` / `damageMultiplier` (30× / 5×). This is the "overworld baseline".
- **The Ender Dragon is just the top entry** in the list. The old dragon-specific config fields and
  the `isEnderDragon` special-case are removed; `minecraft:ender_dragon` goes into the
  `scaled_bosses` tag and is scaled like everything else, only bigger.

Damage stays a multiplier rather than a target, because there is no single "attack" to set a target
on — a boss's damage arrives through many attacks. Damage ramps more gently than health, so late
bosses are dangerous without one-shotting through good armour.

### The curve

Health is the final total; damage is the multiplier on everything the boss deals.

**Overworld — baseline, unlisted, flat 30× / 5×.** Wither (~9,000), Warden (~15,000), Elder Guardian,
Mowzie's Mobs, Mutant Monsters, Born in Chaos, The Conjurer, Illager Invasion, Deeper & Darker.

**Twilight Forest** — the canonical gated ladder:

| Boss | HP | Dmg |
|---|---|---|
| twilightforest:naga | 3,000 | 3× |
| twilightforest:lich | 5,000 | 4× |
| twilightforest:minoshroom | 8,000 | 5× |
| twilightforest:hydra | 12,000 | 6× |
| twilightforest:knight_phantom | 17,000 | 7× |
| twilightforest:ur_ghast | 24,000 | 8× |
| twilightforest:alpha_yeti | 32,000 | 9× |
| twilightforest:snow_queen | 42,000 | 10× |

The Naga and Lich are **no longer exempt**. In a progression they are the entry rung — killable in
upgraded overworld gear — which is a better answer than exempting them, and it is why the flat model
had to exempt them (at a flat ×30 they walled the dimension).

**Aether** — Bronze → Silver → Gold dungeons:

| Boss | HP | Dmg |
|---|---|---|
| aether:slider | 30,000 | 8× |
| aether:valkyrie_queen | 45,000 | 11× |
| aether:sun_spirit | 60,000 | 14× |

**Cataclysm** — by difficulty, Ancient Remnant last:

| Boss | HP | Dmg |
|---|---|---|
| cataclysm:netherite_monstrosity | 15,000 | 8× |
| cataclysm:ender_guardian | 20,000 | 9× |
| cataclysm:ignis | 28,000 | 11× |
| cataclysm:maledictus | 28,000 | 11× |
| cataclysm:the_harbinger | 40,000 | 13× |
| cataclysm:scylla | 40,000 | 13× |
| cataclysm:the_leviathan | 60,000 | 16× |
| cataclysm:ancient_remnant | 90,000 | 20× |

**Blue Skies** — mid-tier, both dimensions:

| Boss | HP | Dmg |
|---|---|---|
| blue_skies:summoner | 25,000 | 9× |
| blue_skies:starlit_crusher | 30,000 | 10× |
| blue_skies:alchemist | 35,000 | 11× |
| blue_skies:arachnarch | 40,000 | 12× |

**Bosses'Rise** — its own chained order:

| Boss | HP | Dmg |
|---|---|---|
| block_factorys_bosses:yeti | 20,000 | 9× |
| block_factorys_bosses:sandworm | 30,000 | 11× |
| block_factorys_bosses:underworld_knight | 45,000 | 14× |
| block_factorys_bosses:infernal_dragon | 70,000 | 18× |
| block_factorys_bosses:kraken | 70,000 | 18× |

**Standalone endgame:**

| Boss | HP | Dmg |
|---|---|---|
| undergarden:forgotten_guardian | 20,000 | 8× |
| deep_aether:eots_controller | 55,000 | 13× |
| lost_aether_content:aerwhale_king | 65,000 | 14× |
| **minecraft:ender_dragon** | **150,000** | **30×** |

The dragon is above every other boss by design — it is the wall at the end of the game.

## How a target is applied

Health becomes a fixed-UUID permanent `AttributeModifier` of `Operation.ADDITION`, amount
`target − baseValue`, where `baseValue` is `AttributeInstance.getBaseValue()` read at spawn. Base 360
Hydra + a +11,640 modifier = 12,000.

`ADDITION` rather than `MULTIPLY_TOTAL` because the target is an absolute number; addition off the
base value lands it exactly. The pack leaves every mod's own health-multiplier config at 1.0, so no
competing `MULTIPLY` modifier distorts the result. (If one is ever turned on, it multiplies the
post-addition total — still monotonic, just larger.)

Everything the flat model got right is kept:

- **Idempotency.** Fixed UUID, `getModifier(UUID)` checked first. `EntityJoinLevelEvent` fires on
  every chunk load and modifiers persist in NBT, so a boss is scaled exactly once, ever.
- **Health topped up on first application only**, so a reloaded, wounded boss stays wounded.
- **Damage on `LivingHurtEvent`, credited to the owner**, because the Ender Dragon does not read
  `ATTACK_DAMAGE` and most Cataclysm damage is projectiles with their own numbers.
- **AttributeFix stays a hard dependency.** Vanilla caps `generic.max_health` at 1024; without the
  cap lifted, every one of these targets clamps to 1024. This is documented in `docs/performance.md`.

Loot (`BossLoot`) is unchanged and still keys off `isScaled`, so the harder a boss is, the better its
drops — which is the point.

## Config

The curve ships as the default value of one list, editable in the toml without a rebuild:

```toml
[bossScaling]
  enabled = true
  # Fallback for any boss not named in bossTiers: the overworld baseline.
  healthMultiplier = 30.0
  damageMultiplier = 5.0
  # The progression. One entry per boss: "entity_id=targetHealth,damageMultiplier".
  # A boss here gets an exact health total; a boss not here gets the flat multipliers above.
  bossTiers = [
    "twilightforest:naga=3000,3",
    "twilightforest:lich=5000,4",
    ... (the full curve above)
    "minecraft:ender_dragon=150000,30"
  ]
  # Never scaled at all, whatever the tag or tiers say.
  exclusions = ["sololeveling"]
```

`enderDragonHealthMultiplier` and `enderDragonDamageMultiplier` are **removed** — the dragon is a tier
entry now. Naga and Lich are **removed from exclusions** — they are the Twilight Forest entry rung.
`sololeveling` stays: its bosses are balanced against the levelling system and are never touched.

## What this deliberately does not do

- **It does not scale minions or adds**, only the bosses themselves. If a fight is trivially long
  rather than hard, boss adds are the next lever.
- **It does not retune per player or per party size.** A four-player party makes a 150k dragon four
  times faster; that is intended (bring friends), not compensated for.
- **A target changed in config does not re-scale already-spawned bosses** — the modifier is persisted
  in their NBT. New spawns get the new number. Same limitation as the flat model.

## Verification

- `/summon twilightforest:knight_phantom` → **17,000 HP**, i.e. more than a summoned Hydra's 12,000,
  even though Knight Phantom's base is 35 and the Hydra's is 360. This is the whole point of the
  redesign and the check that matters.
- Summon the full Twilight Forest ladder: HP increases at every step, Naga lowest, Snow Queen highest.
- `/summon minecraft:ender_dragon` → 150,000, above every other boss.
- Idempotency: summon a boss, reload its chunk, re-read Health — unchanged, not compounded.
- `/summon sololeveling:igris` → unscaled.
- A wounded boss, reloaded, is still wounded.
