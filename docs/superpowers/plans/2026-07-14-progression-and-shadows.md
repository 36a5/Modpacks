# Progression, boss XP, and shadow scaling — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Turn the flat boss multiplier into a per-boss progression curve (overworld easiest, dragon hardest), grant party-shared Solo Leveling levels for boss kills, triple the SL XP rate, and scale shadow soldiers with their Monarch's level.

**Architecture:** All in the `shababparty` mod. `BossScaling` gains a parsed tier map (`id → target HP, damage mult, level reward`) and applies health as an `ADDITION` modifier to hit an absolute target. Two new classes: `BossLevels` (party-shared level grant on boss death) and `ShadowScaling` (shadows scale with owner level, re-synced periodically). One datapack line sets the XP gamerule.

## Global Constraints

- **SRG names, verified with javap.** New ones this plan needs (all confirmed against the jars):
  - `m_21051_(Attribute)`=getAttribute, `m_22111_(UUID)`=getModifier, `m_22120_(AttributeModifier)`=removeModifier, `m_22125_`=addPermanentModifier, `m_22115_()`=getBaseValue, `m_21153_(float)`=setHealth, `m_21233_()`=getMaxHealth
  - `m_204039_(TagKey)`=EntityType.is, `m_6095_()`=getType, `m_203882_`=TagKey.create, `f_256939_`=Registries.ENTITY_TYPE
  - `m_8583_()`=ServerLevel.getAllEntities, `m_21824_()`=isTame, `m_269323_()`=getOwner (proven by PartySupport), `m_217043_()`=getRandom
  - `ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("minecraft","generic.max_health"|"generic.attack_damage"))` — the registry-lookup pattern, not SRG attribute fields.
  - SL capability: `SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY`, fields `Xp`, `MaxXP`, `Level` (all `double`), method `syncPlayerVariables(Entity)`. Reach `getCapability` via `(ICapabilityProvider) player` — the `PartySupport.capabilityOf` pattern.
- **AttributeFix stays a hard dependency** — vanilla caps max_health at 1024.
- **No test framework.** `build.sh` is the type-check; every task has in-game verification.
- **Idempotency:** fixed-UUID modifiers, checked before applying. `EntityJoinLevelEvent` fires per chunk load.

---

## Task 1: Config rework

**File:** `ShababParty.java`

- [ ] Change `BOSS_HEALTH_MULTIPLIER` default `30.0 → 10.0` (keep range).
- [ ] **Remove** `DRAGON_HEALTH_MULTIPLIER` and `DRAGON_DAMAGE_MULTIPLIER` fields and their `defineInRange` calls.
- [ ] Replace the `exclusions` default with `Arrays.asList("sololeveling")` (drop naga/lich).
- [ ] Add `BOSS_TIERS` (`ConfigValue<List<? extends String>>`) whose default is the full tier list below.
- [ ] Add a `[bossLevels]` block: `BOSS_LEVELS_ENABLED` (bool, true), `BOSS_BASE_LEVELS` (int, 3, 0..100).
- [ ] Add a `[shadowScaling]` block: `SHADOW_SCALING_ENABLED` (bool, true), `SHADOW_HEALTH_PER_LEVEL` (double, 0.10, 0..10), `SHADOW_DAMAGE_PER_LEVEL` (double, 0.05, 0..10), `SHADOW_RESYNC_TICKS` (int, 100, 20..1200).

`BOSS_TIERS` default (each string `"id=health,damage,levels"`):

```
mutantmonsters:mutant_zombie=1200,3,4        twilightforest:naga=2000,3,10
mutantmonsters:mutant_skeleton=1000,3,3      twilightforest:lich=3500,4,12
mutantmonsters:mutant_creeper=800,3,3        twilightforest:minoshroom=5500,5,15
mutantmonsters:mutant_enderman=1500,4,5      twilightforest:hydra=8000,6,18
mowziesmobs:frostmaw=1500,3,5                twilightforest:knight_phantom=11000,7,22
mowziesmobs:ferrous_wroughtnaut=1200,3,4     twilightforest:ur_ghast=15000,8,26
mowziesmobs:umvuthi=1400,3,4                 twilightforest:alpha_yeti=20000,9,30
mowziesmobs:sculptor=1600,3,5                twilightforest:snow_queen=26000,10,35
conjurer_illager:conjurer=900,3,3            aether:slider=22000,8,30
illagerinvasion:invoker=800,3,3              aether:valkyrie_queen=32000,11,38
deeperdarker:stalker=1500,4,5                aether:sun_spirit=42000,14,45
born_in_chaos_v1:sir_pumpkinhead=1200,3,4    cataclysm:netherite_monstrosity=12000,8,20
born_in_chaos_v1:lord_pumpkinhead=2000,4,6   cataclysm:ender_guardian=16000,9,25
born_in_chaos_v1:fallen_chaos_knight=1000,3,3 cataclysm:ignis=22000,11,30
born_in_chaos_v1:nightmare_stalker=1000,3,3  cataclysm:maledictus=22000,11,30
minecraft:elder_guardian=1500,3,4            cataclysm:the_harbinger=32000,13,38
minecraft:wither=4000,4,6                    cataclysm:scylla=32000,13,38
minecraft:warden=6000,5,8                    cataclysm:the_leviathan=48000,16,48
blue_skies:summoner=20000,8,25               cataclysm:ancient_remnant=75000,20,60
blue_skies:starlit_crusher=24000,9,28        block_factorys_bosses:yeti=16000,8,22
blue_skies:alchemist=28000,10,30             block_factorys_bosses:sandworm=24000,11,28
blue_skies:arachnarch=32000,11,35            block_factorys_bosses:underworld_knight=36000,14,35
undergarden:forgotten_guardian=16000,8,22    block_factorys_bosses:infernal_dragon=55000,16,42
deep_aether:eots_controller=45000,13,50      block_factorys_bosses:kraken=55000,16,45
lost_aether_content:aerwhale_king=52000,14,50 minecraft:ender_dragon=150000,30,100
```

Validator for the list: `o -> o instanceof String`.

Commit: `feat: config for boss tiers, boss levels, shadow scaling`.

---

## Task 2: Rework `BossScaling` to per-boss targets

**Files:** `BossScaling.java`; add `minecraft:ender_dragon` to `res/data/shababparty/tags/entity_types/scaled_bosses.json`.

- [ ] Add `minecraft:ender_dragon` to the tag's `values`.
- [ ] In `BossScaling`, parse `BOSS_TIERS` once (lazily, cached) into `Map<ResourceLocation, Tier>` where `record Tier(double health, double damage, int levels)`. Parsing: split on `=`, then `,`; ignore malformed lines defensively.
- [ ] Expose `static Tier tierFor(EntityType<?>)` → the tier or `null`, and keep `isScaled` (tag membership minus `exclusions`). Remove `isEnderDragon` and the two dragon config reads.
- [ ] `onJoin`: if `isScaled`, compute health target = `tier != null ? tier.health() : baseValue * BOSS_HEALTH_MULTIPLIER`. Apply as a fixed-UUID `ADDITION` modifier of `target − baseValue` (baseValue = `attribute.m_22115_()`), idempotent via `m_22111_(UUID)`. Top up health on first apply.
- [ ] `onHurt`: damage mult = `tier != null ? tier.damage() : BOSS_DAMAGE_MULTIPLIER`. (unchanged otherwise.)
- [ ] Make the parsed tier map + `isScaled` reachable by `BossLevels` (package-private statics).

Build (`bash tools/shababparty/build.sh`), commit: `feat: BossScaling uses per-boss final-HP targets`.

---

## Task 3: `BossLevels` — party-shared level grant

**File:** new `BossLevels.java`.

- [ ] `@Mod.EventBusSubscriber`, `LivingDeathEvent` at `EventPriority.LOWEST`.
- [ ] Gate on `BOSS_LEVELS_ENABLED` and `BossScaling.isScaled(dead)`.
- [ ] Levels to grant = `tier != null ? tier.levels() : BOSS_BASE_LEVELS`.
- [ ] Recipient set = the killer (via `PartySupport`-style `resolveXpEarner`) plus party members within `XP_SHARE_RADIUS`, same dimension, online — reuse the exact FTB iteration from `PartySupport.onLivingDeath`. To avoid duplicating, add a package-private `PartySupport.partyMembersNear(Player killer, LivingEntity dead, Level level)` returning `List<ServerPlayer>` (killer included) and call it from both.
- [ ] For each recipient: read SL vars, `vars.Xp += levels * vars.MaxXP; vars.syncPlayerVariables(member);`. `LevelUpProcedure.onPlayerTick` consumes it into levels with stats. If `MaxXP <= 0` (unawakened), skip.

Build, commit: `feat: boss kills grant party-shared Solo Leveling levels`.

---

## Task 4: `ShadowScaling` — shadows scale with owner level

**File:** new `ShadowScaling.java`.

- [ ] Tag `minecraft:shadows` via `TagKey.m_203882_(Registries.f_256939_, new ResourceLocation("minecraft","shadows"))`.
- [ ] `isShadow(entity)` = `TamableAnimal` && `getType().m_204039_(shadowsTag)`.
- [ ] `ownerLevel(shadow)` → owner via `m_269323_()`; if `Player`, read `vars.Level`; else `-1` (skip).
- [ ] `scale(shadow, level)`: for `MAX_HEALTH` mult `1 + level*SHADOW_HEALTH_PER_LEVEL`, for `ATTACK_DAMAGE` mult `1 + level*SHADOW_DAMAGE_PER_LEVEL`. Apply as fixed-UUID `MULTIPLY_TOTAL` modifiers (one UUID per attribute). **Re-scaling:** if a modifier exists with a different amount, `removeModifier(UUID)` then re-add; heal to full only when max increases.
- [ ] `EntityJoinLevelEvent` → scale new shadows once.
- [ ] `ServerTickEvent` (phase END), every `SHADOW_RESYNC_TICKS`: for each `ServerLevel` (`event.getServer().m_129785_()`), iterate `level.m_8583_()`, and for each shadow re-run `scale` at the owner's current level. This is what makes a standing army grow when the Monarch levels.

Build, commit: `feat: shadow soldiers scale with their Monarch's level`.

---

## Task 5: Triple the XP gamerule

**Files:** `pack-two/config/paxi/datapacks/slb-jobs/data/slb_jobs/functions/load.mcfunction` and the mirror under `server/run/config/paxi/datapacks/slb-jobs/...`.

- [ ] Append to both `load.mcfunction`:
  ```mcfunction
  # Triple Solo Leveling's XP rate (mod default 10). Boss kills and normal kills both scale.
  gamerule soloLevelingXPMultiplier 30
  ```

Commit: `feat: triple Solo Leveling XP rate (gamerule 10 -> 30)`.

---

## Task 6: Ship 1.8.0, live config, publish

- [ ] `build.sh` `VERSION=1.8.0`; rebuild; confirm one jar + tag inside jar (`unzip -p ... scaled_bosses.json`).
- [ ] `cd pack-two && ../../tools/packwiz.exe refresh`.
- [ ] **Edit live server config** `server/run/config/shababparty-common.toml`: set `healthMultiplier = 10.0`, and **delete the old `[bossScaling]` `bossTiers`/`exclusions`/dragon lines** so Forge rewrites them from the new defaults — the safest way to pick up the new tier list and the removed dragon fields is to remove the whole `[bossScaling]` block and let the mod regenerate it. The new `[bossLevels]`/`[shadowScaling]` blocks generate fresh.
  - Verify after first server start that `bossTiers` in the live toml has ~50 entries.
- [ ] Update `docs/guides/content.md` boss warning to the new numbers (overworld ~1-6k, dimensions ramp, dragon 150k, "bosses give levels", "shadows scale with your level").
- [ ] Commit, fast-forward `master`, push, wait for CI + Pages green, verify Pages serves 1.8.0.

---

## Verification (in game, after server restart)

- Mutant Zombie ≈ 1,200 HP (was 4,500). Frostmaw ≈ 1,500 (was 7,500).
- Knight Phantom (11,000) tankier than Hydra (8,000) despite lower base.
- Kill a boss in a party → all nearby party members gain the tier's levels (panel `;`), with stats.
- `/gamerule soloLevelingXPMultiplier` = 30.
- Summon a shadow at low level, raise Level, wait 5s → same shadow is tankier.
- Ender Dragon 150,000; reload a boss's chunk → HP unchanged (idempotent).
