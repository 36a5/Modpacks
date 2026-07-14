# Monarch of White Flames fixes, the after-image leak, and the content guide

**Date:** 2026-07-14
**Status:** approved, not yet implemented

Four pieces of work, in dependency order. Parts 1 and 3 are the same bug seen from two ends:
the after-image leak is both the "clones never vanish" complaint and a large part of the
memory/FPS complaint, so Part 1 must land before Part 3 can be measured.

---

## Part 1 — After-images never despawn

### The bug

Pressing `V` as Monarch of White Flames enters the Ultra Instinct counter stance. Every
counter leaves an after-image where the player was standing. Those after-images never
disappear.

### Root cause

`UltraInstinct.leaveAfterImage()` spawns the entity like this:

```java
final AfterImageEntity image = type.m_20615_(level);   // EntityType.create
image.m_7678_(x, y, z, yaw, 0.0F);                     // moveTo
level.m_7967_(image);                                  // Level.addFreshEntity
```

The mod's despawn timer is not on the entity. It lives in
`AfterImageOnInitialEntitySpawnProcedure`, which (verified from the jar's bytecode) does:

```java
entity.setNoGravity(true);
level.playSound(..., "entity.wither.shoot", NEUTRAL, 1F, 1F);
SololevelingMod.queueServerWork(10, () -> {
    level.sendParticles(...);
    entity.discard();
});
```

That procedure is invoked from exactly one place: `AfterImageEntity.finalizeSpawn()`
(`m_6518_`). **`Level.addFreshEntity` does not call `finalizeSpawn`** — only natural spawning,
spawn eggs, and `EntityType.spawn()` do. So the 10-tick `discard()` is never queued and the
after-image lives forever.

### Why it is also a memory leak

`AfterImageEntity extends Monster` and implements GeckoLib's `GeoEntity`. Each leaked
after-image is therefore:

- a ticking mob with attributes and an `AnimatableInstanceCache`,
- rendered with a GeckoLib rig when in view,
- **written into chunk NBT on save**, so it survives restarts and is re-loaded every session.

They accumulate without bound across every session a player has ever played. This is the
reported "fine at first, degrades over time, then kicked out for memory" pattern.

### The fix

Two layers, both in `UltraInstinct`.

**Layer 1 — spawn it the way the mod intended.** After `addFreshEntity`, call the mod's own
spawn procedure:

```java
AfterImageOnInitialEntitySpawnProcedure.execute(level, x, y, z, image);
```

This restores the intended behaviour: no gravity, the `entity.wither.shoot` cue, and a
particle-poof `discard()` at +10 ticks. It reuses finished mod code rather than
reimplementing it, which is the same principle the V ability was built on.

**Layer 2 — a watchdog, which is the actual guarantee.** Layer 1 fixes new spawns only, and
depends on `queueServerWork`, a scheduled runnable that a restart mid-flight drops. It does
nothing about the after-images already sitting in players' region files.

So: subscribe to `EntityJoinLevelEvent`. Every `AfterImageEntity` that enters a **server**
level — ours, or one loading off disk — is given a hard deadline. A `ServerTickEvent` sweep
discards anything past it and drops it from the tracking list.

This is what clears the backlog. A leaked after-image dies the moment its chunk loads, so
existing worlds heal themselves as players move around. No world edit, no `/kill` command,
no world reset.

The tracking list is bounded: every entry is removed within the deadline, or when the entity
is already gone (`isRemoved()`), whichever comes first. It cannot become the next leak.

### Config

```toml
[ultraInstinct]
  # Hard deadline for any after-image, in ticks. The mod's own despawn fires at 10 ticks;
  # this is the backstop that catches after-images the mod's scheduled despawn missed,
  # including ones already leaked into existing worlds by the old bug.
  afterImageLifetimeTicks = 40
```

### Rejected alternatives

- **Call `finalizeSpawn` ourselves** instead of the procedure. It works, but it needs a
  `DifficultyInstance` and a `MobSpawnType` we would be inventing, and it drags in whatever
  `Monster.finalizeSpawn` does (equipment rolls, difficulty scaling) that we do not want.
  Calling the procedure directly is narrower.
- **`/kill @e[type=sololeveling:after_image]` in the slb-jobs datapack.** Only reaches loaded
  chunks, so it leaves the backlog in unloaded ones. The watchdog covers those for free.

---

## Part 2 — Remove the C-key cooldown for Monarch of White Flames

### Current behaviour

`C` is `key.sololeveling.ability_3` → `Ability3OnKeyPressedProcedure`. Its `JOB == 4`
(Monarch of White Flames) branch is:

```java
if (!(entity instanceof LivingEntity le && le.hasEffect(SololevelingModMobEffects.JOB_COOLDOWN_3.get()))) {
    StormBurstProcedure.execute(world, x, y, z, entity);
}
```

and `StormBurstProcedure` starts by applying the cooldown to the caster:

```java
new MobEffectInstance(SololevelingModMobEffects.JOB_COOLDOWN_3.get(), 200, 0, false, false)
```

200 ticks = **10 seconds**.

### The fix

A Forge `MobEffectEvent.Applicable` handler: if the entity is a player whose Solo Leveling
`JOB == 4` and the effect is `job_cooldown_3`, set the result to `DENY`. Forge's
`LivingEntity.canBeAffected` honours `DENY` and `addEffect` returns false, so the effect never
lands and the `hasEffect` gate above is never true.

No mixin is needed. The other three jobs use the same `job_cooldown_3` effect for their own
`C` ability, and dispatching on `JOB == 4` leaves them untouched.

### Config

```toml
[abilities]
  # Monarch of White Flames' C ability (Storm Burst) normally puts a 10s job_cooldown_3
  # effect on the caster. False removes it for White Flames only; the other three jobs keep
  # their own C cooldown.
  whiteFlamesAbility3Cooldown = false
```

### Rejected alternative

A `@Redirect` mixin on `StormBurstProcedure`'s `addEffect` call. It works, but it is a mixin
where a plain Forge event will do, and it would remove the cooldown for anyone who ever calls
Storm Burst rather than for White Flames specifically.

---

## Part 3 — FPS and the out-of-memory kicks

Players on 16 GB machines with 8 GB allocated play fine, degrade, then get kicked with a
"allocate more memory" message; relaunching without changing anything works, and it repeats.
That is heap exhaustion from a leak, not an undersized heap.

Work in this order. Nothing here is "add mods and hope".

1. **Land Part 1.** The after-image leak is a real, confirmed, unbounded leak of ticking mobs
   that persist to disk. It is the first thing to fix and may be the whole problem. Anything
   measured before it lands is measuring the leak.

2. **JVM flags.** `client/install.ps1:514` currently emits:
   `-Xmx8G -Xms2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+DisableExplicitGC`
   Set `-Xms` equal to `-Xmx` (a heap growing 2G→8G churns on every resize) and tune G1 for a
   heap this size instead of leaving it at defaults.

3. **Audit the known memory/CPU hogs and report, do not silently remove.** Candidates already
   in the pack: Farsight (client-caches far chunks — directly costs RAM), Entity Texture
   Features + Entity Model Features, Sound Physics Remastered, Wakes Reforged, Dynamic Lights
   Reforged, and the Xaero map caches. Each gets a line saying what it costs and what is lost
   by dropping it. The owner decides.

4. **New optimisation mods.** The pack already runs Xenon, Radium Reforged, ModernFix,
   FerriteCore, EntityCulling, ImmediatelyFast, BadOptimizations, Krypton, Clumps,
   AllTheLeaks, Alternate Current, LMFT, Structure Layout Optimizer, Particle Core, Ixeris and
   Spark. There is very little left that is real. The one genuine candidate is **More Culling**;
   it is added only after its Forge 1.20.1 and Xenon compatibility are verified. The list is
   not padded to look busy.

Explicitly out of scope: Nvidium (breaks under shaders), Starlight/Canary/C2ME/Noisium/VMP
(Fabric-only, or conflict with Radium).

---

## Part 4 — The content guide

Players are only engaging with the Solo Leveling systems because nothing tells them what else
is in the pack. `docs/guides/systems.md:213` covers all of the pack's exploration content in
twelve lines.

Write `docs/guides/content.md`: a player-facing tour of every content mod in the pack, in
English, matching the tone and emoji-heading style of `systems.md`. It covers, per mod:

- **Dimensions** — what the place is, and *how you actually get there*.
- **Bosses** — where each one lives or how it is summoned, and what it drops.
- **Mobs** — what is new and where it appears.
- **Dungeons and structures** — what to look for and roughly where.
- **Items and weapons** — the notable gear and which key fires its ability.

`systems.md:213`'s "Extra worlds, bosses & dungeons" stub is replaced with a short pointer to
the new guide, so there is one source of truth rather than two drifting summaries.

---

## Verification

- **Part 1:** in game, hold the stance and take repeated hits. After-images appear and vanish
  within half a second. `/data get entity @e[type=sololeveling:after_image,limit=1]` finds
  nothing a second later. Load a world that already has leaked after-images and confirm the
  count drops to zero as chunks load.
- **Part 2:** as White Flames, press `C` repeatedly. Storm Burst fires every time, with no
  `job_cooldown_3` effect appearing in the effects HUD. As Frost Monarch or Grand Mage, `C`
  still puts the 10s cooldown up.
- **Part 3:** a player runs a long session and does not get kicked. Spark's heap summary shows
  no growing `AfterImageEntity` count.
- **Part 4:** the guide is read by someone who has only played the Solo Leveling half, and they
  can name three things to go do.
