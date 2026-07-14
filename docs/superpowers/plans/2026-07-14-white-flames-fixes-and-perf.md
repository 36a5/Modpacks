# Monarch of White Flames fixes, the after-image leak, and the content guide — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop Ultra Instinct's after-images leaking forever (which is both the "clones never vanish" bug and a large part of the out-of-memory kicks), remove the 10-second C-key cooldown for Monarch of White Flames, tune the client JVM, and write a player-facing guide to everything in the pack.

**Architecture:** Two new classes in the existing `shababparty` companion mod, both driven by plain Forge events rather than mixins. `AfterImages` owns the entire lifecycle of Solo Leveling's `AfterImageEntity`: it spawns them through the mod's own spawn procedure, and independently guarantees every after-image in a server level dies on a deadline — which is what clears the ones already leaked into players' region files. `JobCooldown` denies the `job_cooldown_3` mob effect for JOB 4 only, which is the gate Solo Leveling's C ability checks.

**Tech Stack:** Java 17, Minecraft 1.20.1, Forge 47.4.18, packwiz. Built by `tools/shababparty/build.sh` — plain `javac` against the production jars on disk, no Gradle.

## Global Constraints

- **Minecraft methods must be written in SRG names.** `build.sh` compiles against production-mapped jars and there is no reobfuscation step. Every SRG name used in this plan has been verified with `javap` against the real jars — do not substitute readable names.
  - `m_146870_()` = `Entity.discard()`
  - `m_213877_()` = `Entity.isRemoved()`
  - `m_20615_(Level)` = `EntityType.create()`
  - `m_7678_(x,y,z,yaw,pitch)` = `Entity.moveTo()`
  - `m_7967_(Entity)` = `Level.addFreshEntity()`
  - `m_9236_()` = `Entity.level()`
  - `m_46467_()` = `Level.getGameTime()`
  - `m_5776_()` = `Level.isClientSide()`
  - `m_146908_()` = `Entity.getYRot()`
  - `m_19544_()` = `MobEffectInstance.getEffect()`
  - `m_20182_()` = `Entity.position()`
  - `m_7096_() / m_7098_() / m_7094_()` = `Vec3.x() / y() / z()`
- **Forge and Solo Leveling classes are NOT remapped.** `getEntity()`, `getLevel()`, `setResult()`, `getCapability()` and every `net.solocraft.*` name is written plainly.
- **Forge patches `getCapability` onto `Entity` at boot.** It does not exist in the vanilla jar we compile against, so a player must be cast to `ICapabilityProvider` first. This is the existing pattern in `Ability4WipMixin` and `PartySupport.capabilityOf`.
- **There is no test framework in this repo, and this plan does not invent one.** A Forge mod compiled against SRG production jars cannot be meaningfully unit-tested without a large harness that would be its own project. The type-check performed by `build.sh` is the automated gate; every task also carries explicit in-game verification with exact commands. Do not fabricate passing tests.
- **No new mods are added.** More Culling — the one perf mod that was a real candidate — has no Forge build (Fabric/Quilt/NeoForge only), and forcing it through Sinytra Connector alongside Xenon risks crashing the pack. The pack's optimisation stack is already saturated.

---

## File Structure

| File | Responsibility |
|---|---|
| `tools/shababparty/src/dev/alshabab/shababparty/AfterImages.java` | **New.** The whole after-image lifecycle: spawn one correctly, and guarantee every after-image anywhere dies on a deadline. |
| `tools/shababparty/src/dev/alshabab/shababparty/JobCooldown.java` | **New.** Denies `job_cooldown_3` for JOB 4, removing the White Flames C cooldown. |
| `tools/shababparty/src/dev/alshabab/shababparty/UltraInstinct.java` | **Modify.** Loses `leaveAfterImage`; delegates to `AfterImages.spawn`. |
| `tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java` | **Modify.** Two new config values. |
| `tools/shababparty/build.sh` | **Modify.** Version bump, and delete stale `shababparty-*.jar` before installing the new one. |
| `client/install.ps1` | **Modify.** One shared, tuned JVM-flags string used by both launcher paths. |
| `docs/performance.md` | **New.** What each known memory/CPU hog in the pack costs, for the owner to decide on. |
| `docs/guides/content.md` | **New.** Player-facing tour of every content mod. |
| `docs/guides/systems.md` | **Modify.** The 12-line stub at line 213 becomes a pointer to `content.md`. |

`AfterImages` is a separate class from `UltraInstinct` on purpose. The watchdog now governs *every* after-image in the world, not only the ones the counter stance makes — that is a different responsibility from "the V ability", and burying it inside `UltraInstinct` would hide it.

---

## Task 1: After-image lifecycle

The bug: `UltraInstinct.leaveAfterImage` spawns with `Level.addFreshEntity`, but Solo Leveling's despawn timer lives in `AfterImageOnInitialEntitySpawnProcedure`, which is only ever reached from `AfterImageEntity.finalizeSpawn()` — and `addFreshEntity` does not call `finalizeSpawn`. So the timer is never queued. `AfterImageEntity extends Monster`, ticks, carries a GeckoLib rig, and is written to chunk NBT, so every clone ever left reloads each session and accumulates forever.

**Files:**
- Create: `tools/shababparty/src/dev/alshabab/shababparty/AfterImages.java`
- Modify: `tools/shababparty/src/dev/alshabab/shababparty/UltraInstinct.java` (remove `leaveAfterImage`, lines 145-157; change the call site at line 119; drop three now-unused imports)
- Modify: `tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java` (add `AFTER_IMAGE_LIFETIME_TICKS`)

**Interfaces:**
- Consumes: `ShababParty.MOD_ID`, `ShababParty.Config`.
- Produces: `AfterImages.spawn(ServerLevel level, ServerPlayer player, Vec3 at)` → `void`. Called by `UltraInstinct.counter`. `ShababParty.Config.AFTER_IMAGE_LIFETIME_TICKS` → `ForgeConfigSpec.IntValue`.

- [ ] **Step 1: Add the config value to `ShababParty.java`**

Add the field alongside the other `ULTRA_INSTINCT_*` declarations (after line 36):

```java
        public static final ForgeConfigSpec.IntValue AFTER_IMAGE_LIFETIME_TICKS;
```

And define it inside the `b.push("ultraInstinct")` block, immediately before `b.pop();` (line 124):

```java
            AFTER_IMAGE_LIFETIME_TICKS = b
                    .comment("Hard deadline for an after-image left by the counter, in ticks. 20 ticks = 1 second.",
                            "",
                            "Solo Leveling despawns its own after-images 10 ticks after they appear, but only when",
                            "they are spawned through finalizeSpawn - and addFreshEntity, which is how anything",
                            "outside the mod spawns one, does not call it. Every after-image this server has ever",
                            "produced therefore lived forever, ticking and saved into chunk NBT.",
                            "",
                            "This is the backstop. Every after-image entering a server level is discarded once this",
                            "many ticks have passed - including ones already leaked into a world by the old bug,",
                            "which die as soon as their chunk loads. Raise it only to make the after-image linger.")
                    .defineInRange("afterImageLifetimeTicks", 40, 10, 200);
```

- [ ] **Step 2: Create `AfterImages.java`**

```java
package dev.alshabab.shababparty;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.solocraft.entity.AfterImageEntity;
import net.solocraft.init.SololevelingModEntities;
import net.solocraft.procedures.AfterImageOnInitialEntitySpawnProcedure;

/**
 * The whole life of Solo Leveling's after-image, which the mod itself never quite finished.
 *
 * <h2>The leak</h2>
 * AfterImageEntity's despawn is not on the entity. It lives in
 * AfterImageOnInitialEntitySpawnProcedure - no gravity, an entity.wither.shoot cue, and a
 * queueServerWork(10, () -> discard()) - and that procedure is reached from exactly one place:
 * AfterImageEntity.finalizeSpawn. Level.addFreshEntity does not call finalizeSpawn. Only natural
 * spawning, spawn eggs and EntityType.spawn do.
 *
 * So an after-image spawned the ordinary way never despawns. And AfterImageEntity extends Monster
 * and implements GeoEntity: each leaked one is a ticking mob with a GeckoLib rig that gets *written
 * into chunk NBT*, so it comes back every session. They accumulate without bound. That is both the
 * "the clones never vanish" report and a large part of the out-of-memory kicks.
 *
 * <h2>Two layers</h2>
 * {@link #spawn} calls the mod's own procedure, so a new after-image behaves exactly as Solo
 * Leveling intended: it poofs at 10 ticks with particles.
 *
 * {@link #onJoin} is the guarantee. queueServerWork is a scheduled runnable that a restart drops on
 * the floor, and it does nothing whatever about the after-images already sitting in players' region
 * files. So every AfterImageEntity entering a *server* level - ours, someone else's, or one loading
 * off disk from before this fix - is put on a deadline and discarded when it expires. Existing
 * worlds heal themselves as players walk around: no world edit, no /kill, no reset.
 *
 * The tracking list cannot become the next leak. Every entry leaves it within the deadline, or
 * sooner if the entity is already gone.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AfterImages {

    /** After-images awaiting their deadline. Server thread only. */
    private static final List<Tracked> TRACKED = new ArrayList<>();

    private record Tracked(AfterImageEntity entity, long deadline) {
    }

    /** Leave an after-image where the player was standing. Cosmetic: failure must not cost the counter. */
    public static void spawn(final ServerLevel level, final ServerPlayer player, final Vec3 at) {
        try {
            final EntityType<AfterImageEntity> type = SololevelingModEntities.AFTER_IMAGE.get();
            final AfterImageEntity image = type.m_20615_(level);
            if (image == null) {
                return;
            }
            image.m_7678_(at.m_7096_(), at.m_7098_(), at.m_7094_(), player.m_146908_(), 0.0F);
            level.m_7967_(image);

            // The line whose absence was the bug. onJoin has already put this entity on a deadline
            // by the time addFreshEntity returns, so even if this throws, it still despawns.
            AfterImageOnInitialEntitySpawnProcedure.execute(
                    level, at.m_7096_(), at.m_7098_(), at.m_7094_(), image);
        } catch (final Throwable ignored) {
            // A missing or changed after-image entity must not cost the player his counter.
        }
    }

    @SubscribeEvent
    public static void onJoin(final EntityJoinLevelEvent event) {
        if (event.getLevel().m_5776_()) {
            return;
        }
        if (!(event.getEntity() instanceof AfterImageEntity image)) {
            return;
        }
        final long deadline = event.getLevel().m_46467_()
                + ShababParty.Config.AFTER_IMAGE_LIFETIME_TICKS.get();
        TRACKED.add(new Tracked(image, deadline));
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRACKED.isEmpty()) {
            return;
        }
        final Iterator<Tracked> images = TRACKED.iterator();
        while (images.hasNext()) {
            final Tracked tracked = images.next();
            final AfterImageEntity image = tracked.entity();

            // Already gone: the mod's own 10-tick despawn fired, or the chunk unloaded.
            if (image.m_213877_()) {
                images.remove();
                continue;
            }
            // Read the time off the entity's own level rather than the event: an after-image can be
            // in any dimension, and each level keeps its own game time.
            if (image.m_9236_().m_46467_() >= tracked.deadline()) {
                image.m_146870_();
                images.remove();
            }
        }
    }

    private AfterImages() {
    }
}
```

- [ ] **Step 3: Point `UltraInstinct` at it**

In `UltraInstinct.java`, replace the call at line 119:

```java
        leaveAfterImage(level, player, from);
```

with:

```java
        AfterImages.spawn(level, player, from);
```

Delete the whole `leaveAfterImage` method (lines 145-157, including its javadoc comment `/** The mod's own after-image, spawned where the player was. Cosmetic: failure is not fatal. */`).

Delete these three imports, now unused:

```java
import net.minecraft.world.entity.EntityType;
import net.solocraft.entity.AfterImageEntity;
import net.solocraft.init.SololevelingModEntities;
```

Then update the class javadoc: the paragraph under `<h2>What "animation" means here</h2>` says the after-image "is spawned at the position the player vanishes from". Append to that paragraph:

```
 * Its lifecycle - including the despawn Solo Leveling wrote but never reached - is
 * {@link AfterImages}.
```

- [ ] **Step 4: Build, which is the type-check**

Run:

```bash
bash tools/shababparty/build.sh
```

Expected: `built and installed: shababparty-1.2.0.jar` with no `javac` errors. A wrong SRG name or a bad Forge signature fails here — this is the gate. If `javac` reports `cannot find symbol` on any `m_*` name, the name is wrong; re-verify it with `javap` against the jar rather than guessing another.

- [ ] **Step 5: Commit**

```bash
git add tools/shababparty/src/dev/alshabab/shababparty/AfterImages.java \
        tools/shababparty/src/dev/alshabab/shababparty/UltraInstinct.java \
        tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java
git commit -m "fix: after-images never despawned, and piled up in chunk NBT forever

UltraInstinct spawned them with Level.addFreshEntity. Solo Leveling's despawn
lives in AfterImageOnInitialEntitySpawnProcedure, reached only from
AfterImageEntity.finalizeSpawn - which addFreshEntity does not call. The
10-tick discard was therefore never queued.

AfterImageEntity extends Monster and is saved to chunk NBT, so every clone a
player ever left was a ticking mob that came back each session. That is the
'clones stay behind' report and a large part of the out-of-memory kicks.

AfterImages now spawns through the mod's own procedure, and puts every
after-image entering a server level on a deadline - including the ones already
leaked into region files, which die as their chunks load. Existing worlds heal
themselves; no world edit needed."
```

---

## Task 2: Remove the C-key cooldown for Monarch of White Flames

`C` is `key.sololeveling.ability_3`. `Ability3OnKeyPressedProcedure`'s `JOB == 4` branch fires `StormBurstProcedure` only when the caster does **not** have the `job_cooldown_3` mob effect — and `StormBurstProcedure` opens by applying that effect to the caster for 200 ticks. Deny the effect and the gate is never shut.

**Files:**
- Create: `tools/shababparty/src/dev/alshabab/shababparty/JobCooldown.java`
- Modify: `tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java` (add `WHITE_FLAMES_ABILITY_3_COOLDOWN`)

**Interfaces:**
- Consumes: `ShababParty.MOD_ID`, `ShababParty.Config`.
- Produces: `ShababParty.Config.WHITE_FLAMES_ABILITY_3_COOLDOWN` → `ForgeConfigSpec.BooleanValue`, default `false`.

- [ ] **Step 1: Add the config value to `ShababParty.java`**

Add the field alongside the other ability declarations (after line 32, `GRAND_MAGE_ABILITY_4`):

```java
        public static final ForgeConfigSpec.BooleanValue WHITE_FLAMES_ABILITY_3_COOLDOWN;
```

And define it inside the `b.push("abilities")` block, immediately before its `b.pop();` (line 93):

```java
            WHITE_FLAMES_ABILITY_3_COOLDOWN = b
                    .comment("Whether Monarch of White Flames' C ability (Storm Burst) puts a cooldown up.",
                            "",
                            "Solo Leveling gives every job's C ability a 10-second cooldown. It is not a timer:",
                            "the ability applies a job_cooldown_3 mob effect to the caster for 200 ticks, and its",
                            "own branch refuses to fire while that effect is present.",
                            "",
                            "False stops that effect from ever landing on a Monarch of White Flames, so C can be",
                            "pressed as fast as the player likes. The other three jobs share the same effect for",
                            "their own C ability and are untouched.")
                    .define("whiteFlamesAbility3Cooldown", false);
```

- [ ] **Step 2: Create `JobCooldown.java`**

```java
package dev.alshabab.shababparty;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.solocraft.init.SololevelingModMobEffects;
import net.solocraft.network.SololevelingModVariables;

/**
 * Removes the cooldown on Monarch of White Flames' C ability.
 *
 * Solo Leveling does not implement job-ability cooldowns as a timer. Ability3OnKeyPressedProcedure's
 * JOB == 4 branch reads:
 *
 *     if (!(entity instanceof LivingEntity le && le.hasEffect(JOB_COOLDOWN_3)))
 *         StormBurstProcedure.execute(...);
 *
 * and StormBurstProcedure's first act is to put JOB_COOLDOWN_3 on the caster for 200 ticks. The
 * cooldown *is* the mob effect. Stop the effect landing and the gate never closes.
 *
 * MobEffectEvent.Applicable rather than a mixin on StormBurstProcedure: Forge's
 * LivingEntity.canBeAffected posts this event and honours DENY, and addEffect goes through
 * canBeAffected - so this is the supported way to refuse an effect, with no bytecode surgery. It is
 * also the narrower fix. All four jobs share JOB_COOLDOWN_3 for their own C ability, and dispatching
 * on JOB == 4 leaves the other three exactly as they were; a redirect inside StormBurstProcedure
 * would instead uncap the ability for anyone who ever came to call it.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JobCooldown {

    private static final double JOB_WHITE_FLAMES = 4.0D;

    @SubscribeEvent
    public static void onEffectApplicable(final MobEffectEvent.Applicable event) {
        if (ShababParty.Config.WHITE_FLAMES_ABILITY_3_COOLDOWN.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getEffectInstance().m_19544_() != SololevelingModMobEffects.JOB_COOLDOWN_3.get()) {
            return;
        }

        // Cast to ICapabilityProvider first: Forge patches getCapability onto Entity as the game
        // boots, so the vanilla jar we compile against has no such method. Same reason, and the same
        // workaround, as Ability4WipMixin and PartySupport.capabilityOf.
        final SololevelingModVariables.PlayerVariables vars =
                ((ICapabilityProvider) player)
                        .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                        .orElse(null);

        if (vars != null && vars.JOB == JOB_WHITE_FLAMES) {
            event.setResult(Event.Result.DENY);
        }
    }

    private JobCooldown() {
    }
}
```

- [ ] **Step 3: Build**

Run:

```bash
bash tools/shababparty/build.sh
```

Expected: `built and installed: shababparty-1.2.0.jar`, no errors.

- [ ] **Step 4: Commit**

```bash
git add tools/shababparty/src/dev/alshabab/shababparty/JobCooldown.java \
        tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java
git commit -m "feat: no C-key cooldown for Monarch of White Flames

Solo Leveling's job-ability cooldown is a mob effect, not a timer: Storm Burst
applies job_cooldown_3 for 200 ticks and Ability3OnKeyPressedProcedure refuses
to fire while the caster has it. Denying the effect for JOB 4 via
MobEffectEvent.Applicable removes the cooldown without touching the other three
jobs, which share the same effect."
```

---

## Task 3: Ship the jar

The mod is a **local** jar in `pack-two/mods/shababparty-1.2.0.jar`, tracked by hash in `pack-two/index.toml`. A version bump changes the filename, so the old jar must be deleted — two shababparty jars in `mods/` would make Forge refuse to boot on a duplicate mod id. `build.sh` currently copies the new jar in without removing the old.

**Files:**
- Modify: `tools/shababparty/build.sh` (version, and clean stale jars)
- Modify: `pack-two/index.toml`, `pack-two/pack.toml` (regenerated by `packwiz refresh`)

**Interfaces:**
- Consumes: the sources from Tasks 1 and 2.
- Produces: `pack-two/mods/shababparty-1.3.0.jar`, indexed.

- [ ] **Step 1: Bump the version and clean stale jars in `build.sh`**

Change line 10:

```bash
VERSION=1.3.0
```

Replace the two install lines (63-67) so a previous build cannot linger:

```bash
JAR="$BUILD/shababparty-$VERSION.jar"
( cd "$BUILD/classes" && jar --create --file "$JAR" --manifest META-INF/MANIFEST.MF -C . . )

# Two shababparty jars in mods/ is a duplicate mod id and Forge will not boot. A version bump
# changes the filename, so the old one has to go rather than sit alongside the new one.
rm -f "$REPO/pack-two/mods"/shababparty-*.jar "$MODS"/shababparty-*.jar

cp "$JAR" "$REPO/pack-two/mods/"
cp "$JAR" "$MODS/"
```

- [ ] **Step 2: Rebuild**

Run:

```bash
bash tools/shababparty/build.sh
ls pack-two/mods/shababparty-*.jar
```

Expected: `built and installed: shababparty-1.3.0.jar`, and `ls` prints exactly one jar — `pack-two/mods/shababparty-1.3.0.jar`.

- [ ] **Step 3: Refresh the packwiz index**

Run:

```bash
cd pack-two && ../../tools/packwiz.exe refresh && cd ..
git diff --stat pack-two/index.toml pack-two/pack.toml
```

Expected: `index.toml` drops the `shababparty-1.2.0.jar` entry and gains `shababparty-1.3.0.jar`; `pack.toml`'s index hash changes.

- [ ] **Step 4: Commit**

`build.sh` has already deleted the 1.2.0 jar from the working tree, so stage the whole `mods/`
directory with `-A` — that records the delete and the add together. Staging the new jar by path
alone would leave the old one still tracked.

```bash
git add -A pack-two/mods/
git add tools/shababparty/build.sh pack-two/index.toml pack-two/pack.toml
git status --short pack-two/mods/   # expect: D shababparty-1.2.0.jar, A shababparty-1.3.0.jar
git commit -m "build: shababparty 1.3.0, and delete stale jars on install

Two shababparty jars in mods/ is a duplicate mod id and Forge will not boot.
build.sh copied the new jar in without removing the old one, which only worked
because the version never changed."
```

- [ ] **Step 5: Verify in game — this is the real test**

Start the server and join as a Monarch of White Flames (`/slr <player> job MonarchOfWhiteFlames` — the allowlist in `slb-jobs` keeps it on `Abdulrhman-S`; see `pack-two/config/paxi/datapacks/slb-jobs/data/slb_jobs/functions/tick.mcfunction`).

**After-images despawn.** Press `V`, let a mob hit you several times, then:

```
/execute if entity @e[type=sololeveling:after_image] run say STILL THERE
```

Expected: after-images visibly poof within half a second of each counter, and the command prints nothing once you stop being hit. Run it again a few seconds later — still nothing.

**The backlog clears.** Before starting, count what a leaked world already holds:

```
/execute store result score #n dummy run execute if entity @e[type=sololeveling:after_image]
```

or simply look — fly around a heavily-fought area. Expected: leaked after-images vanish shortly after their chunk loads, and the count trends to zero as you travel. Nothing needs to be killed by hand.

**C has no cooldown.** As White Flames, press `C` repeatedly. Expected: Storm Burst fires every press, and no `job_cooldown_3` appears in the effects HUD. Then `/slr <player> job FrostMonarch` and press `C` twice — expected: the second press does nothing for ~10 seconds, i.e. the other jobs keep their cooldown.

If any of these fail, stop and report. Do not proceed to Task 4 on a broken build.

---

## Task 4: JVM flags

Players on 16 GB machines get 8 GB, play fine, degrade, then get kicked with an out-of-memory message; a relaunch fixes it and it repeats. Task 1 addresses the leak that causes that. This addresses the heap that has to absorb it: `-Xms2G -Xmx8G` makes the JVM grow the heap in stages and churn on every resize, and `MaxGCPauseMillis=200` at default G1 sizing gives long stop-the-world pauses that read as stutter.

Both launcher paths set flags in different places, and only the vanilla-launcher path sets any at all today.

**Files:**
- Modify: `client/install.ps1` (define the flags once at line ~96; use at line 279 and line 514)

**Interfaces:**
- Consumes: `$allocGb` (already computed at line 96).
- Produces: `$jvmArgs`, a single string used by both the CurseForge instance and the vanilla launcher profile.

- [ ] **Step 1: Define the flags once, next to where RAM is sized**

In `client/install.ps1`, after line 96 (`$allocGb = ...`) and before the `if ($allocGb -lt 6)` warning, insert:

```powershell
# One definition, used by both the CurseForge instance and the Minecraft Launcher profile.
#
# -Xms equal to -Xmx: a heap that grows 2G -> 8G resizes repeatedly under load, and every resize is
# a full pause. Reserve it all up front.
#
# The G1 block is Aikar's: G1 defaults are tuned for a small young generation, which is exactly
# wrong for Minecraft, where almost every allocation is short-lived garbage. A large young gen plus
# an early IHOP means G1 collects concurrently and rarely has to stop the world.
$jvmArgs = "-Xms${allocGb}G -Xmx${allocGb}G " +
           "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=50 " +
           "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch " +
           "-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M " +
           "-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 " +
           "-XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 " +
           "-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 " +
           "-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1"
```

- [ ] **Step 2: Give the CurseForge instance the same flags**

`Install-CurseForgeInstance` sets `javaArgsOverride = $null` on a new instance and preserves whatever was there on an existing one, so CurseForge players run on its stock flags today. In the block of `$inst[...]` assignments that runs for both new and existing instances, immediately after line 278 (`$inst["isMemoryOverride"] = $true`) and its `allocatedMemory` line, add:

```powershell
    $inst["javaArgsOverride"]  = $script:jvmArgs
```

Note `$script:` — `$jvmArgs` is defined at script scope and `Install-CurseForgeInstance` is a function, so an unqualified `$jvmArgs` would resolve to nothing there and silently write an empty override.

- [ ] **Step 3: Use it for the vanilla launcher profile**

Replace line 514:

```powershell
            $javaArgs   = "-Xmx${allocGb}G -Xms2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+DisableExplicitGC"
```

with:

```powershell
            $javaArgs   = $jvmArgs
```

- [ ] **Step 4: Check the script still parses**

A stray quote in the multi-line string concatenation would only surface when a player runs the
installer, so parse it here. Run this via the **PowerShell** tool, not Bash — the `$` sigils and
`[ref]` syntax do not survive a bash quoting round-trip:

```powershell
$errs = $null
[void][System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path client/install.ps1), [ref]$null, [ref]$errs)
if ($errs) { $errs } else { "OK: no parse errors" }
```

Expected: `OK: no parse errors`.

Then confirm the flags actually landed in both places:

```powershell
Select-String -Path client/install.ps1 -Pattern 'jvmArgs'
```

Expected: three hits — the definition near line 96, the `$inst["javaArgsOverride"]` assignment, and
the `$javaArgs = $jvmArgs` line. If the `javaArgsOverride` hit is missing, CurseForge players are
still on stock flags and Step 2 was skipped.

- [ ] **Step 5: Commit**

```bash
git add client/install.ps1
git commit -m "perf: size the client heap up front and tune G1

-Xms2G -Xmx8G grows the heap in stages and pauses on every resize, and G1's
default young generation is tuned for workloads unlike Minecraft, where nearly
every allocation is short-lived garbage. Reserve the heap up front and give G1 a
large young gen with an early IHOP so it collects concurrently.

CurseForge players were getting none of this: the instance writer left
javaArgsOverride null, so they ran on stock flags. Both launcher paths now share
one definition."
```

---

## Task 5: The performance audit

The spec is explicit that this task **reports and does not remove**. Each of these is a taste call about what the pack is, and the owner decides.

**Files:**
- Create: `docs/performance.md`

- [ ] **Step 1: Confirm each mod is actually still in the pack before writing a line about it**

Run:

```bash
cd pack-two/mods && ls | grep -iE "farsight|entity-texture-features|entity-model-features|sound-physics|wakes|dynamiclights|xaero|3dskinlayers|not-enough-animations|subtle-effects|particle-effects"
```

Write up only what this prints. Do not describe a mod the pack does not have.

- [ ] **Step 2: Write `docs/performance.md`**

Structure it as: a short statement of what was actually wrong (the after-image leak, fixed in 1.3.0; the JVM flags, fixed in `install.ps1`), then a table of the remaining candidates with three columns — **Mod**, **What it costs**, **What you lose by dropping it** — then a closing note that the pack's optimisation stack (Xenon, Radium Reforged, ModernFix with `dynamic_resources` already on, FerriteCore, EntityCulling, ImmediatelyFast, BadOptimizations, Krypton, Clumps, AllTheLeaks, Alternate Current, LMFT, Structure Layout Optimizer, Particle Core, Ixeris, Spark) is saturated, and that **More Culling has no Forge build** so there is no perf mod left worth adding.

Every "what it costs" claim must be specific about the *kind* of cost — RAM, CPU tick time, GPU/draw calls — not a vague "it's heavy". If a cost is not known with confidence, say that it is a suspect to profile with Spark rather than asserting a number.

- [ ] **Step 3: Commit**

```bash
git add docs/performance.md
git commit -m "docs: performance audit — what each remaining hog costs

The after-image leak and the JVM flags were the two real problems and both are
fixed. What is left is a set of taste calls about what the pack is, so this
lists what each costs and what dropping it loses, and leaves the decision to the
owner."
```

---

## Task 6: The content guide

Players are only touching the Solo Leveling half of the pack because nothing tells them what else is in it. `docs/guides/systems.md:213` covers all of the pack's exploration content in twelve lines.

**Files:**
- Create: `docs/guides/content.md`
- Modify: `docs/guides/systems.md` (lines 213-225 become a pointer)

- [ ] **Step 1: Build the inventory from the pack itself, not from memory**

Run:

```bash
cd pack-two/mods && ls | sed 's/\.pw\.toml$//' | sed 's/\.jar$//' | sort
```

Group every entry into: **Dimensions**, **Bosses**, **Mobs**, **Dungeons & structures**, **Items & weapons**, **Systems** (already covered by `systems.md` — skip these), or **Library/perf/UI** (skip). Work from this list. A mod that is not in it does not go in the guide.

- [ ] **Step 2: Verify every "how do I get there" before writing it**

This is the step that makes the guide worth having, and the one it is easiest to get wrong. **Do not write a portal recipe, a boss summon, or a structure location from memory.** Players will follow these instructions and a wrong one wastes their evening.

For each dimension and each summonable boss, confirm the entry method against the mod's own documentation — its wiki, its Modrinth/CurseForge page, or its in-game Patchouli book if the pack ships one. Where the pack ships a guidebook (Patchouli is in the mod list), say so and point at it rather than transcribing it.

Where a fact cannot be confirmed, write what *is* known ("the Otherside is reached from the Ancient City") and send the player to JEI (`R` / `U` on an item) rather than inventing the missing half.

- [ ] **Step 3: Write `docs/guides/content.md`**

Match `systems.md`'s voice: emoji headings, short lines, `|` tables for keybinds, written to a player and not to a developer. Cover, per section:

- **🌍 Dimensions** — what the place is, what is worth going for, and exactly how to get there.
- **💀 Bosses** — where each lives or how it is summoned, roughly how hard, and what it drops.
- **🐺 Mobs** — what is new and where it shows up.
- **🏰 Dungeons & structures** — what to look for and where it generates.
- **⚔️ Items & weapons** — the notable gear, and which key fires its ability. Cross-reference `docs/keybinds-shabab2.md`; Cataclysm's armour abilities are on `[`, `]`, `,`, `.` because Solo Leveling owns `Z`/`X`/`C`/`V`.

Open with a two-sentence "you are probably only playing the Solo Leveling half — here is everything else" framing, because that is the actual problem this doc exists to solve.

- [ ] **Step 4: Replace the stub in `systems.md`**

`systems.md` lines 213-225 currently summarise the same ground in twelve lines. Two summaries will drift. Replace the body of the `## 🗺️ Extra worlds, bosses & dungeons` section with a short pointer:

```markdown
## 🗺️ Extra worlds, bosses & dungeons

The pack is much bigger than the Solo Leveling systems above — a dozen dimensions, dozens of
bosses, and a world full of dungeons most players never open.

**→ [The full content guide](content.md)** — every dimension and how to reach it, every boss and
how to find it, and what all of it drops.

Most bosses drop unique gear, and many armour pieces have an **ability** on a key — check the item
tooltip. Cataclysm's armour abilities moved off `X`/`V`/`C`/`Y` (Solo Leveling owns those now) onto
four keys in a row: `[` = ability, `]` = helmet, `,` = chestplate, `.` = boots.
```

- [ ] **Step 5: Commit**

```bash
git add docs/guides/content.md docs/guides/systems.md
git commit -m "docs: a player-facing guide to everything in the pack

Players only engage with the Solo Leveling systems because nothing tells them
what else is installed. systems.md covered a dozen dimensions, dozens of bosses
and the whole structure set in twelve lines; it now points at a real guide.

Entry methods are taken from each mod's own documentation, not from memory - a
wrong portal recipe wastes a player's evening."
```

---

## Verification

Task 3 Step 5 is the real gate and must pass before Tasks 4-6 are considered done:

- After-images poof within half a second; `/execute if entity @e[type=sololeveling:after_image]` finds nothing once you stop being hit.
- A world that already contains leaked after-images sheds them as its chunks load, with no manual `/kill`.
- `C` as White Flames fires every press with no `job_cooldown_3` in the effects HUD; `C` as Frost Monarch still has its 10-second cooldown.
- A long play session no longer ends in an out-of-memory kick. This is the one that takes real time to confirm — it needs a player to actually sit and play, and it is the whole point of the exercise.
