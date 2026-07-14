# Boss Scaling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every non-Solo-Leveling boss ×30 max health and ×5 outgoing damage (Ender Dragon ×100 / ×10), because Solo Leveling players currently kill them in seconds.

**Architecture:** One new `BossScaling` class in the pack's own `shababparty` mod, driven by two Forge events. Membership comes from a datapack tag the mod ships in its own jar (`shababparty:scaled_bosses`), which nests `#forge:bosses` and adds the mods that don't register there. Health is a fixed-UUID permanent `AttributeModifier` so it is idempotent across chunk reloads; damage is intercepted on `LivingHurtEvent` because the Ender Dragon does not read `ATTACK_DAMAGE` at all.

**Tech Stack:** Java 17, Minecraft 1.20.1, Forge 47.4.18, packwiz. Built by `tools/shababparty/build.sh` — plain `javac` against the production jars on disk, no Gradle.

## Global Constraints

- **Minecraft methods and fields must be written in SRG names.** `build.sh` compiles against production-mapped jars with no reobfuscation step. Every SRG name below was verified with `javap` against the real jars — do not substitute readable names or invent new ones.
  - `m_21051_(Attribute)` = `LivingEntity.getAttribute()` → `AttributeInstance`
  - `m_22111_(UUID)` = `AttributeInstance.getModifier()`
  - `m_22125_(AttributeModifier)` = `AttributeInstance.addPermanentModifier()`
  - `m_21153_(float)` = `LivingEntity.setHealth()`
  - `m_21233_()` = `LivingEntity.getMaxHealth()`
  - `m_204039_(TagKey)` = `EntityType.is()`
  - `m_6095_()` = `Entity.getType()`
  - `m_7639_()` = `DamageSource.getEntity()` (the owner — a dragon, not its fireball)
  - `m_203882_(ResourceKey, ResourceLocation)` = `TagKey.create()`
  - `f_256939_` = `Registries.ENTITY_TYPE`
  - `m_5776_()` = `Level.isClientSide()`
  - `m_9236_()` = `Entity.level()`
  - `m_20078_()` = `Entity.getEncodeId()` — the registry id string, e.g. `"twilightforest:naga"`
- **`Attributes.MAX_HEALTH` is looked up through `ForgeRegistries.ATTRIBUTES`, not the SRG field.** This is the pattern `UltraInstinct.playAt` already uses for sounds, and its comment explains why: the constant-holder classes are SRG-mangled and picking a field by hand is a guess that fails silently.
- **Forge and Solo Leveling classes are NOT remapped.** `getSource()`, `getAmount()`, `setAmount()`, `getEntity()`, `getLevel()` are written plainly.
- **No test framework exists in this repo and this plan does not invent one.** `build.sh` is the type-check; every task carries in-game verification with exact commands.
- **Idempotency is the defect this design exists to prevent.** `EntityJoinLevelEvent` fires on every chunk load and attribute modifiers persist in NBT. A boss must be scaled exactly once, ever.

---

## File Structure

| File | Responsibility |
|---|---|
| `tools/shababparty/src/dev/alshabab/shababparty/BossScaling.java` | **New.** The whole feature: tag membership, exclusions, the health modifier, the damage interception. |
| `tools/shababparty/res/data/shababparty/tags/entity_types/scaled_bosses.json` | **New.** Which entities are bosses. A datapack tag, so it can be extended later without a rebuild. |
| `tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java` | **Modify.** New `[bossScaling]` config block. |
| `tools/shababparty/build.sh` | **Modify.** Version 1.3.0 → 1.4.0. |
| `docs/guides/content.md` | **Modify.** Tell players the bosses are 30× harder. |

`build.sh` already does `cp -r "$HERE/res/." "$BUILD/classes/"`, so anything under `res/` ships inside the jar. The tag file needs no build change.

---

## Task 1: The boss tag

**Files:**
- Create: `tools/shababparty/res/data/shababparty/tags/entity_types/scaled_bosses.json`

**Interfaces:**
- Produces: the entity-type tag `shababparty:scaled_bosses`, read by `BossScaling` in Task 2.

- [ ] **Step 1: Write the tag**

Every id below was read out of the mods' own jars. `#forge:bosses` is *nested*, not copied, so a mod
update that adds a boss to the Forge tag is picked up for free. `forge:bosses` itself is **not**
modified — other mods read it for boss bars and instakill immunity, and injecting a Mutant Zombie
into it could change behaviour we have not audited.

Entries whose mod is absent are silently ignored by the tag loader, so this file is safe if a mod is
later dropped from the pack.

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

The Ender Dragon is deliberately absent: `BossScaling` special-cases it with its own multipliers, so
putting it in the tag would give it the ×30 numbers instead.

- [ ] **Step 2: Validate the JSON**

Run:

```bash
python -c "import json;d=json.load(open('tools/shababparty/res/data/shababparty/tags/entity_types/scaled_bosses.json'));print(len(d['values']),'entries; replace =',d['replace'])"
```

Expected: `22 entries; replace = False`

---

## Task 2: `BossScaling`

**Files:**
- Create: `tools/shababparty/src/dev/alshabab/shababparty/BossScaling.java`
- Modify: `tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java` (new `[bossScaling]` block)

**Interfaces:**
- Consumes: `ShababParty.MOD_ID`, `ShababParty.Config`, the `shababparty:scaled_bosses` tag from Task 1.
- Produces: `ShababParty.Config.BOSS_SCALING_ENABLED` → `BooleanValue`; `BOSS_HEALTH_MULTIPLIER`, `BOSS_DAMAGE_MULTIPLIER`, `DRAGON_HEALTH_MULTIPLIER`, `DRAGON_DAMAGE_MULTIPLIER` → `DoubleValue`; `BOSS_SCALING_EXCLUSIONS` → `ConfigValue<List<? extends String>>`.

- [ ] **Step 1: Add the config block to `ShababParty.java`**

Add the fields after `AFTER_IMAGE_LIFETIME_TICKS`:

```java
        public static final ForgeConfigSpec.BooleanValue BOSS_SCALING_ENABLED;
        public static final ForgeConfigSpec.DoubleValue BOSS_HEALTH_MULTIPLIER;
        public static final ForgeConfigSpec.DoubleValue BOSS_DAMAGE_MULTIPLIER;
        public static final ForgeConfigSpec.DoubleValue DRAGON_HEALTH_MULTIPLIER;
        public static final ForgeConfigSpec.DoubleValue DRAGON_DAMAGE_MULTIPLIER;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BOSS_SCALING_EXCLUSIONS;
```

`java.util.List` and `java.util.Arrays` must be imported at the top of the file.

Then define the block after the `ultraInstinct` section's `b.pop();`, before `SPEC = b.build();`:

```java
            b.push("bossScaling");
            BOSS_SCALING_ENABLED = b
                    .comment("Scale up every boss in the shababparty:scaled_bosses tag.",
                            "",
                            "Solo Leveling players out-level the rest of the pack badly enough that its bosses die",
                            "in seconds. This gives them back some weight. Solo Leveling's own bosses keep their",
                            "own numbers - they are not in the tag, and the exclusions below guard them anyway.")
                    .define("enabled", true);

            BOSS_HEALTH_MULTIPLIER = b
                    .comment("Max health multiplier for every boss in the tag except the Ender Dragon.",
                            "Applied once per boss as a permanent attribute modifier, never re-applied.")
                    .defineInRange("healthMultiplier", 30.0D, 1.0D, 1000.0D);

            BOSS_DAMAGE_MULTIPLIER = b
                    .comment("Multiplier on all damage those bosses deal.",
                            "",
                            "Applied to the damage itself rather than to the ATTACK_DAMAGE attribute, because most",
                            "boss damage never touches that attribute - Cataclysm's bosses do their real damage with",
                            "projectiles and area attacks that carry their own numbers, and the Ender Dragon does not",
                            "read ATTACK_DAMAGE at all. Scaling the attribute would look like it worked and change",
                            "almost nothing.")
                    .defineInRange("damageMultiplier", 5.0D, 1.0D, 100.0D);

            DRAGON_HEALTH_MULTIPLIER = b
                    .comment("The Ender Dragon gets its own numbers. Vanilla is 200 HP, so 100 = 20000.")
                    .defineInRange("enderDragonHealthMultiplier", 100.0D, 1.0D, 1000.0D);

            DRAGON_DAMAGE_MULTIPLIER = b
                    .comment("Multiplier on all damage the Ender Dragon deals.")
                    .defineInRange("enderDragonDamageMultiplier", 10.0D, 1.0D, 100.0D);

            BOSS_SCALING_EXCLUSIONS = b
                    .comment("Never scaled, whatever the tag says. A bare namespace excludes all of its entities.",
                            "",
                            "  sololeveling         - its bosses are balanced against the levelling system already",
                            "  twilightforest:naga  - Twilight Forest's tutorial boss, 120 HP, meant for iron armour.",
                            "  twilightforest:lich  - It and the Naga gate the entire Twilight Forest ladder; at 30x",
                            "                         they would wall off a dimension instead of making it harder.")
                    .defineList("exclusions",
                            Arrays.asList("sololeveling", "twilightforest:naga", "twilightforest:lich"),
                            o -> o instanceof String);
            b.pop();
```

- [ ] **Step 2: Create `BossScaling.java`**

```java
package dev.alshabab.shababparty;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Makes the pack's bosses a fight again.
 *
 * Solo Leveling's levelling curve leaves players strong enough that the rest of the pack's bosses die
 * in seconds. Everything in the shababparty:scaled_bosses tag gets its max health and its outgoing
 * damage multiplied. Solo Leveling's own bosses are untouched: they live in the mod's own
 * minecraft:soloboss tag, never reach ours, and are excluded by namespace as well.
 *
 * <h2>Health: one permanent modifier, one time</h2>
 * The multiplier is an AttributeModifier on MAX_HEALTH with a *fixed* UUID.
 *
 * That matters more than it looks. Attribute modifiers are serialised into the mob's NBT, and
 * EntityJoinLevelEvent fires again every time its chunk reloads. Re-applying blindly would multiply a
 * boss by 30 on every chunk load until its health overflowed - walk away and come back three times and
 * the Hydra has 97 million HP. Keying the modifier to a constant UUID and checking for it first makes
 * the operation idempotent: a boss is scaled exactly once, ever, however often it is loaded.
 *
 * Health is topped up to the new maximum only on that first application, so returning to a boss you
 * already wounded does not heal it.
 *
 * <h2>Damage: the hurt event, not the attribute</h2>
 * Scaling the ATTACK_DAMAGE attribute would do *nothing at all* to the Ender Dragon, which does not
 * read it - its damage is hardcoded in its attack phases. Most of Cataclysm's bosses are the same:
 * their real damage arrives as projectiles and area attacks carrying their own configured values
 * (Voidrunedamage, AbyssBlastdamage, DeathLaserdamage). The attribute would look scaled and the fight
 * would feel identical.
 *
 * So damage is multiplied where it actually lands. LivingHurtEvent fires for every source - melee,
 * projectile, spell, breath, explosion - and DamageSource.getEntity() names the *owner*, so a dragon's
 * fireball is credited to the dragon rather than to the fireball.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossScaling {

    /** The bosses we scale. Shipped in this jar; a datapack may extend it. */
    private static final TagKey<EntityType<?>> SCALED_BOSSES = TagKey.m_203882_(
            Registries.f_256939_, new ResourceLocation(ShababParty.MOD_ID, "scaled_bosses"));

    private static final ResourceLocation ENDER_DRAGON = new ResourceLocation("minecraft", "ender_dragon");

    /**
     * Constant, so the modifier can be recognised on a boss that has already been scaled and loaded
     * back off disk. Randomising it would re-scale every boss on every chunk load.
     */
    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("5b8a1f6c-3d21-4f7e-9c14-8e2a7d0b46f3");

    private static final String HEALTH_MODIFIER_NAME = "shababparty:boss_health";

    private BossScaling() {
    }

    @SubscribeEvent
    public static void onJoin(final EntityJoinLevelEvent event) {
        if (event.getLevel().m_5776_() || !ShababParty.Config.BOSS_SCALING_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity boss) || !isScaled(boss)) {
            return;
        }

        // MAX_HEALTH via the registry rather than the SRG-mangled Attributes field: same reason, and
        // the same workaround, as UltraInstinct.playAt uses for sounds.
        final Attribute maxHealth = ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation("minecraft", "generic.max_health"));
        if (maxHealth == null) {
            return;
        }
        final AttributeInstance attribute = boss.m_21051_(maxHealth);
        if (attribute == null || attribute.m_22111_(HEALTH_MODIFIER_ID) != null) {
            // Already scaled - loaded off disk with the modifier still in its NBT. Leave it alone.
            return;
        }

        final double multiplier = isEnderDragon(boss)
                ? ShababParty.Config.DRAGON_HEALTH_MULTIPLIER.get()
                : ShababParty.Config.BOSS_HEALTH_MULTIPLIER.get();

        // MULTIPLY_TOTAL: final = base * (1 + amount), so 30x is an amount of 29.
        attribute.m_22125_(new AttributeModifier(
                HEALTH_MODIFIER_ID, HEALTH_MODIFIER_NAME, multiplier - 1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL));

        // Only on first application. A boss loaded back at half health stays at half health.
        boss.m_21153_(boss.m_21233_());
    }

    @SubscribeEvent
    public static void onHurt(final LivingHurtEvent event) {
        if (!ShababParty.Config.BOSS_SCALING_ENABLED.get()) {
            return;
        }
        final DamageSource source = event.getSource();

        // The owner, not the projectile: a dragon's fireball is the dragon's damage.
        final Entity attacker = source.m_7639_();
        if (!(attacker instanceof LivingEntity boss) || !isScaled(boss)) {
            return;
        }

        final double multiplier = isEnderDragon(boss)
                ? ShababParty.Config.DRAGON_DAMAGE_MULTIPLIER.get()
                : ShababParty.Config.BOSS_DAMAGE_MULTIPLIER.get();

        event.setAmount((float) (event.getAmount() * multiplier));
    }

    /** In the tag, and not excluded. */
    private static boolean isScaled(final LivingEntity entity) {
        return (entity.m_6095_().m_204039_(SCALED_BOSSES) || isEnderDragon(entity))
                && !isExcluded(entity);
    }

    private static boolean isEnderDragon(final LivingEntity entity) {
        return ENDER_DRAGON.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.m_6095_()));
    }

    /**
     * An exclusion is either a full id ("twilightforest:naga") or a bare namespace ("sololeveling"),
     * which excludes everything that mod registers.
     */
    private static boolean isExcluded(final LivingEntity entity) {
        final ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.m_6095_());
        if (id == null) {
            return false;
        }
        final List<? extends String> exclusions = ShababParty.Config.BOSS_SCALING_EXCLUSIONS.get();
        return exclusions.contains(id.toString()) || exclusions.contains(id.m_135827_());
    }
}
```

`m_135827_()` is `ResourceLocation.getNamespace()`.

- [ ] **Step 3: Build, which is the type-check**

Run:

```bash
bash tools/shababparty/build.sh
```

Expected: `built and installed: shababparty-1.3.0.jar` with no `javac` errors. If `javac` reports
`cannot find symbol` on any `m_*` or `f_*` name, that name is wrong — re-verify it with `javap`
against the jar rather than trying another guess.

- [ ] **Step 4: Commit**

```bash
git add tools/shababparty/src/dev/alshabab/shababparty/BossScaling.java \
        tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java \
        tools/shababparty/res/data/shababparty/tags/entity_types/scaled_bosses.json
git commit -m "feat: scale every non-Solo-Leveling boss

Solo Leveling players kill the rest of the pack's bosses in seconds. Everything
in the new shababparty:scaled_bosses tag gets 30x max health and 5x outgoing
damage; the Ender Dragon gets 100x and 10x.

Damage is multiplied on LivingHurtEvent rather than through the ATTACK_DAMAGE
attribute, because the Ender Dragon does not read that attribute and neither do
most of Cataclysm's bosses, whose damage arrives as projectiles carrying their
own numbers. Scaling the attribute would have looked correct and changed nothing.

Health is a fixed-UUID permanent modifier. Attribute modifiers persist in NBT and
EntityJoinLevelEvent fires on every chunk load, so a randomised or unchecked
modifier would re-multiply a boss every time a player walked back to it.

The mod ships its own tag rather than adding to forge:bosses, which other mods
read for boss bars and instakill immunity."
```

---

## Task 3: Ship 1.4.0

**Files:**
- Modify: `tools/shababparty/build.sh` (`VERSION=1.4.0`)
- Modify: `pack-two/index.toml`, `pack-two/pack.toml` (regenerated)

- [ ] **Step 1: Bump the version**

`tools/shababparty/build.sh` line 10:

```bash
VERSION=1.4.0
```

- [ ] **Step 2: Rebuild and confirm no stale jar**

Run:

```bash
bash tools/shababparty/build.sh
ls pack-two/mods/shababparty-*.jar
```

Expected: `built and installed: shababparty-1.4.0.jar`, and exactly one jar —
`pack-two/mods/shababparty-1.4.0.jar`. (`build.sh` deletes stale jars; two would be a duplicate mod
id and Forge would refuse to boot.)

- [ ] **Step 3: Confirm the tag actually shipped inside the jar**

The tag is useless if `res/` didn't make it in. Run:

```bash
unzip -p pack-two/mods/shababparty-1.4.0.jar data/shababparty/tags/entity_types/scaled_bosses.json | head -5
```

Expected: the JSON, starting `{ "replace": false,`. If this prints nothing, `build.sh`'s
`cp -r "$HERE/res/." "$BUILD/classes/"` did not pick the file up and the feature will silently do
nothing.

- [ ] **Step 4: Refresh the packwiz index**

```bash
cd pack-two && ../../tools/packwiz.exe refresh && cd ..
```

Expected: `Index refreshed!`, and `index.toml` now names `shababparty-1.4.0.jar`.

- [ ] **Step 5: Commit**

```bash
git add -A pack-two/mods/
git add tools/shababparty/build.sh pack-two/index.toml pack-two/pack.toml
git status --short pack-two/mods/   # expect: D shababparty-1.3.0.jar, A shababparty-1.4.0.jar
git commit -m "build: shababparty 1.4.0 — boss scaling"
```

---

## Task 4: Tell the players

A pack where every boss suddenly has 30× health, with no announcement, reads as a bug.

**Files:**
- Modify: `docs/guides/content.md` (the `## 💀 Bosses` section)

- [ ] **Step 1: Add the warning at the top of the Bosses section**

Insert immediately under the `## 💀 Bosses` heading, before the Cataclysm subsection:

```markdown
> ⚠️ **Every boss on this page is scaled up.** Solo Leveling levels you far past what these mods
> were balanced for — bosses were dying in seconds. So they now have **30× the health** and hit for
> **5× the damage**. The Ender Dragon has **100× health** (20,000) and hits for **10×**.
>
> Two exceptions, both deliberate: **Solo Leveling's own bosses are untouched**, and Twilight
> Forest's **Naga and Lich** keep their normal numbers so the Twilight Forest ladder is still
> enterable.
>
> Bring friends. Bring a party (`P`). These are not solo fights any more.
```

- [ ] **Step 2: Commit**

```bash
git add docs/guides/content.md
git commit -m "docs: tell players the bosses are scaled"
```

---

## Verification

In game, on the server, with the new jar loaded.

**Idempotency — the one that matters.** Summon a boss, note its health, then force its chunk to
reload and check again:

```
/summon twilightforest:hydra ~ ~ ~
/data get entity @e[type=twilightforest:hydra,limit=1] Health
```

Expected: 30× its normal maximum. Now walk far enough away for the chunk to unload, come back, and
re-read. Expected: **the same number, not 900×.** If it grew, the fixed-UUID check is not working and
the feature must be reverted before anyone plays on it.

**Exclusions.**

```
/summon twilightforest:naga ~ ~ ~     -> 120 HP, unscaled
/summon sololeveling:igris ~ ~ ~      -> unscaled
```

**Ender Dragon.** In the End: 20,000 HP on the boss bar, and its bite takes roughly ten times the
usual chunk out of you. This is the check that the `LivingHurtEvent` path works where an attribute
would not have.

**Damage.** Let a Cataclysm boss hit you in known armour and compare to before — roughly 5×.

**Wounded bosses stay wounded.** Damage a boss to half, unload its chunk, return. It must still be at
half, not healed to full.
