# Boss scaling — make every non-Solo-Leveling boss a real fight again

**Date:** 2026-07-14
**Status:** approved, not yet implemented

Solo Leveling players out-scale the rest of the pack badly enough that bosses "melt — dead in
seconds". Every boss that is not a Solo Leveling boss gets **×30 max health** and **×5 damage
dealt**. The Ender Dragon gets **×100 health** and **×10 damage**. Solo Leveling's own bosses are
left exactly as they are.

---

## Where the scaling lives

A new `BossScaling` class in the pack's own `shababparty` mod, driven entirely by Forge events. No
mixins.

### Rejected: each mod's own config

L_Ender's Cataclysm ships `EnderGuardianHealthMultiplier`, `IgnisHealthMultiplier` and friends (all
at `1.0` today), and setting those would be the "native" way to scale Cataclysm. It is not enough:

- **Only Cataclysm has them.** Twilight Forest, the Aether, Blue Skies, Mowzie's, Mutant Monsters
  and vanilla expose no such knob.
- **Nothing anywhere exposes a damage multiplier.**

So it would cover a fraction of the bosses and none of the damage. Those knobs stay at `1.0`, so
there is exactly one place scaling happens and no chance of two multipliers stacking.

### Rejected: a datapack

1.20.1 datapacks cannot set base attributes per entity type. There is no data-only way to do this.

---

## Health — a permanent attribute modifier

On `EntityJoinLevelEvent`, a boss gets an `AttributeModifier` on `MAX_HEALTH`
(`MULTIPLY_TOTAL`) with a **fixed UUID**, added with `addPermanentModifier`, then healed to full.

The fixed UUID is the whole point. Attribute modifiers are serialised into the mob's NBT, so the
modifier survives a save. When the chunk reloads, `EntityJoinLevelEvent` fires *again* — and if we
blindly re-applied, a boss would be multiplied by 30 on every single chunk load until its health
overflowed. Checking `getModifier(UUID) != null` first makes the operation idempotent: a boss is
scaled exactly once, ever, no matter how many times it is loaded.

Health is set to the new maximum **only on first application**, so reloading a boss you have already
damaged does not heal it back to full.

## Damage — `LivingHurtEvent`, not `ATTACK_DAMAGE`

This is the part that is easy to get wrong.

Scaling the `ATTACK_DAMAGE` attribute **would do nothing to the Ender Dragon.** The dragon's damage
is hardcoded in its attack phases; it does not read that attribute. Most Cataclysm bosses are the
same — their real damage comes from projectiles and area attacks with their own configured values
(`Voidrunedamage`, `AbyssBlastdamage`, `DeathLaserdamage`, and so on), none of which touch
`ATTACK_DAMAGE`. Multiplying the attribute would appear to work and change almost nothing.

So damage is scaled where it actually lands: `LivingHurtEvent`. If the damage source's attacking
entity is a scaled boss, the amount is multiplied. That catches melee, projectiles, spells, breath
and AoE — every mechanism, without knowing anything about how the boss attacks.

The attacker is read from `DamageSource.getEntity()` (the owner) rather than `getDirectEntity()` (the
projectile), so a dragon's fireball is credited to the dragon.

---

## Which entities count as a boss

Forge already defines an entity-type tag, `forge:bosses`, and much of the pack populates it:

| Source | Bosses contributed |
|---|---|
| L_Ender's Cataclysm | ignis, netherite_monstrosity, ender_guardian, the_harbinger, the_leviathan, ancient_remnant, maledictus, scylla |
| Twilight Forest | naga, lich, minoshroom, hydra, knight_phantom, ur_ghast, alpha_yeti, snow_queen, plateau_boss |
| Bosses'Rise | infernal_dragon, yeti, sandworm, underworld_knight, kraken |
| The Aether | slider, valkyrie_queen, sun_spirit |
| Deep Aether | eots_controller, eots_segment |
| Lost Aether Content | aerwhale_king |
| The Undergarden | forgotten_guardian |

**Solo Leveling does not use this tag.** It keeps its thirteen bosses in its own
`minecraft:soloboss` tag, so they are excluded from `forge:bosses` for free. A namespace guard is
kept anyway as belt and braces.

### The mod ships its own tag, and does not touch `forge:bosses`

Several mods are missing from `forge:bosses`: Blue Skies (which keeps its bosses in
`blue_skies:bosses`), Mowzie's Mobs, Mutant Monsters, Born in Chaos, The Conjurer, Deeper and
Darker, Illager Invasion, and vanilla's own Wither, Warden and Elder Guardian.

The obvious move is to add them to `forge:bosses` with a datapack. **We will not.** Other mods read
`forge:bosses` for their own purposes — boss-bar rendering, immunity to instakill effects, exemption
from mob griefing rules. Injecting a Mutant Zombie into it could change behaviour nobody asked for,
in a mod we have not audited.

Instead `shababparty` ships its own tag, `shababparty:scaled_bosses`, inside the jar
(`res/data/shababparty/tags/entity_types/scaled_bosses.json`):

```json
{
  "replace": false,
  "values": [
    "#forge:bosses",
    "#blue_skies:bosses",
    "minecraft:wither",
    "minecraft:warden",
    "minecraft:elder_guardian",
    "mowziesmobs:ferrous_wroughtnaut",
    "mowziesmobs:frostmaw",
    "mowziesmobs:umvuthi",
    "mowziesmobs:sculptor",
    "mutantmonsters:mutant_zombie",
    "mutantmonsters:mutant_skeleton",
    "mutantmonsters:mutant_creeper",
    "mutantmonsters:mutant_enderman",
    "deeperdarker:stalker",
    "conjurer_illager:conjurer",
    "illagerinvasion:invoker",
    "born_in_chaos_v1:sir_pumpkinhead",
    "born_in_chaos_v1:lord_pumpkinhead",
    "born_in_chaos_v1:fallen_chaos_knight",
    "born_in_chaos_v1:nightmare_stalker",
    "born_in_chaos_v1:lifestealer",
    "born_in_chaos_v1:supreme_bonescaller"
  ]
}
```

It nests `#forge:bosses` rather than duplicating it, so a mod update that adds a boss to the Forge
tag is picked up for free. Every id above was read out of the mods' own jars, not recalled.

Entries for entities a given mod does not register are ignored by the tag loader, so this file is
safe even if a mod is later removed from the pack.

The tag is a datapack tag, so the owner can extend or override it later with a Paxi datapack without
rebuilding the mod.

---

## Exclusions

Two kinds, both config:

1. **Solo Leveling.** Any entity in the `sololeveling` namespace is never scaled. It cannot reach
   the tag anyway, but the guard is explicit so a future mod update cannot change that quietly.

2. **Twilight Forest's on-ramp: `twilightforest:naga` and `twilightforest:lich`.**
   The Naga has 120 HP and is the dimension's tutorial boss — it is meant to be killed in iron
   armour, and it *gates the entire Twilight Forest progression ladder*. At ×30 it becomes a
   3,600 HP wall in front of a dimension nobody has opened yet. The Lich is the same for the second
   tier. Everything past them (Minoshroom, Hydra, Knight Phantom, Ur-Ghast, Alpha Yeti, Snow Queen)
   scales normally.

---

## Config

```toml
[bossScaling]
  enabled = true
  # Every boss in the shababparty:scaled_bosses tag.
  healthMultiplier = 30.0
  damageMultiplier = 5.0
  # The Ender Dragon, which gets its own numbers.
  enderDragonHealthMultiplier = 100.0
  enderDragonDamageMultiplier = 10.0
  # Never scaled, whatever the tag says. Namespaces may be given bare ("sololeveling").
  exclusions = ["sololeveling", "twilightforest:naga", "twilightforest:lich"]
```

Every number is a config value, so the pack can be retuned from a text file and a restart — no jar
rebuild, no player re-install.

---

## What this does not do

- **It does not scale boss minions or adds.** Only entities in the tag. The Ender Guardian's bullets,
  Ignis's revenants and the Lich's summons stay vanilla. If the fights end up trivially long rather
  than hard, that is the next knob.
- **It does not touch loot.** A 30× boss drops exactly what it dropped before. Whether that is a fair
  trade is a balance decision for later, deliberately out of scope here.
- **It does not scale the bosses' own resistances or armour.** Health and outgoing damage only.

---

## Verification

- `/summon twilightforest:hydra` → boss bar shows 30× its normal maximum. Kill it, reload the chunk,
  summon another: still 30×, never 900×. This is the idempotency check and it is the one that
  matters.
- Damage a boss, unload its chunk, walk back: it is still damaged, not healed to full.
- `/summon minecraft:ender_dragon` → 20,000 HP. Let it hit you: roughly ten times the usual bite.
  This is the check that the `LivingHurtEvent` path works where an attribute would not have.
- `/summon twilightforest:naga` → **120 HP, unscaled.** The exclusion works.
- `/summon sololeveling:igris` → unscaled.
- A Cataclysm boss takes ~30× the hits to kill and its projectiles hurt ~5× as much.
