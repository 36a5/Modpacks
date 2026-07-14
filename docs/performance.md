# Performance — what was actually wrong, and what is left

> **Unrelated but worth knowing, because it cost a release:** vanilla hard-caps
> `generic.max_health` at **1024** and silently clamps anything above it. The first boss-scaling
> release multiplied a 360 HP Hydra by 30 and produced a 1024 HP Hydra. Nothing logged; the number
> just came out wrong. **AttributeFix** is in the pack to raise that ceiling to 1,000,000. If it is
> ever removed, every boss collapses back to 1024 no matter what the multipliers say.

Players on 16 GB machines with 8 GB allocated were playing fine, degrading over a session, then
being kicked with an "allocate more memory" message. Relaunching without changing anything worked,
and then it happened again.

That is a **leak**, not an undersized heap. 8 GB is a defensible allocation for this pack. A heap
that is merely too small is too small from the first minute; one that fills, empties on restart and
fills again is being filled by something that never lets go.

Two things were actually wrong. Both are fixed.

---

## 1. The after-image leak (fixed in shababparty 1.3.0)

Ultra Instinct — the Monarch of White Flames `V` ability — leaves an after-image every time it
counters a hit. **None of them ever despawned.**

Solo Leveling's despawn for `AfterImageEntity` is not on the entity. It lives in
`AfterImageOnInitialEntitySpawnProcedure`, which queues a `discard()` ten ticks out. That procedure
is reached from exactly one place: `AfterImageEntity.finalizeSpawn()`. And `Level.addFreshEntity` —
which is how `shababparty` spawned them, and how anything outside the mod would — **does not call
`finalizeSpawn`**. Only natural spawning, spawn eggs and `EntityType.spawn` do. So the despawn was
never queued.

Why that costs so much:

- `AfterImageEntity extends Monster`. Every leaked after-image is a **ticking mob** with attributes
  and AI, not an inert decoration.
- It implements GeckoLib's `GeoEntity`, so each one carries an `AnimatableInstanceCache` and is
  rendered through a GeckoLib rig when in view.
- It is **written into chunk NBT on save**. They survived restarts and were re-loaded every session.

So the count only ever went up, across every session every player had ever played. That is the
"clones stay behind" bug and the out-of-memory kicks: one bug, seen from two ends.

**The fix does not require a world reset.** `AfterImages` now puts *every* after-image entering a
server level on a deadline — including the ones already sitting in players' region files, which are
discarded as soon as their chunk loads. Worlds heal themselves as players move around.

## 2. The client JVM flags (fixed in `client/install.ps1`)

Two problems:

- **`-Xms2G -Xmx8G`.** The heap started at 2 GB and grew to 8 GB in stages, pausing on every resize.
  It is now `-Xms8G -Xmx8G`: reserved up front, no resizing.
- **CurseForge players were getting no flags at all.** The instance writer set `javaArgsOverride` to
  `null` on new instances and preserved whatever was there on existing ones, so they ran on
  CurseForge's stock JVM settings. Both launcher paths now share one tuned definition.

G1's defaults assume a small young generation. Minecraft is the opposite case — nearly every
allocation is short-lived garbage — so the flags now give it a large young gen and an early IHOP,
which lets G1 collect concurrently instead of stopping the world.

---

## What is left: the taste calls

Each of these costs something real. None is a bug, and dropping any of them changes what the pack
*is*, so none has been touched. Decide which you want.

The cost column says what *kind* of resource each spends. Where a figure is not known with
confidence it says so, rather than inventing one — Spark is already in the pack (`/spark profiler`,
`/spark heapsummary`) and will give real numbers on the actual machines.

| Mod | What it costs | What you lose by dropping it |
|---|---|---|
| **Farsight** | **RAM.** It keeps chunks the server has stopped sending in the client's memory so they stay rendered past render distance. That cached terrain is heap, and it grows with how far a player has travelled. The most likely remaining memory contributor. | Long views. Terrain past render distance (10) goes back to being empty sky. |
| **Entity Texture Features** | **RAM + GPU.** Loads and caches extra per-entity texture variants for every mob type in a 387-mod pack. | Random mob skin variants; some resource-pack features. |
| **Entity Model Features** | **RAM + CPU.** Custom entity models, evaluated per entity. Pairs with ETF; they are usually dropped together. | Custom entity models from resource packs. |
| **Sound Physics Remastered** | **CPU.** Raycasts audio against geometry for reverb and occlusion, on every sound, continuously. A known frame-time cost in caves and dense builds. | Echo and muffling. It is a big part of how the pack *feels* — this one is a real loss. |
| **Wakes Reforged** | **GPU + CPU.** Simulates and renders water wakes behind entities. | Boat/swim wakes. |
| **Dynamic Lights Reforged** | **CPU.** Re-lights chunks as light-emitting entities and held items move, which forces chunk re-meshes. | Held torches lighting the world. |
| **Xaero's Minimap + World Map + XaeroPlus** | **Disk + RAM.** The world map caches every chunk a player has seen, to disk and partly in memory. Grows without limit on a long-lived world. | The map. Realistically non-negotiable on a server this size. |
| **3D Skin Layers** | **GPU.** Extra geometry per player. Cheap with few players, less so in a crowd. | 3D hat/jacket layers on skins. |
| **Not Enough Animations / Subtle Effects / Particle Effects** | **CPU + GPU**, individually small. Worth grouping only if you are hunting the last few frames. | Animation and particle polish. |

**Suggested order if you want to cut**: Farsight first (it is the one that is plausibly still costing
memory), then Entity Texture Features + Entity Model Features together. Leave Sound Physics unless
you have to — it costs frames, but it is doing more for the pack's atmosphere than anything else on
this list.

Before cutting anything, **measure**. Have a player run a normal session and then:

```
/spark heapsummary      # what is actually holding the heap
/spark profiler --timeout 60
```

That turns this table from a list of suspects into a list of facts.

---

## What is *not* worth adding

The pack's optimisation stack is saturated. It already runs:

Xenon (the Sodium-derived renderer) · Radium Reforged (Lithium) · ModernFix — with
`dynamic_resources` already enabled, which is the single largest memory option available ·
FerriteCore · AllTheLeaks · Fix GPU Memory Leak · EntityCulling · ImmediatelyFast · BadOptimizations ·
Krypton · Clumps · Alternate Current · LMFT · Structure Layout Optimizer · Particle Core · Ixeris ·
Cupboard · Connectivity · Packet Fixer · Memory Settings · Spark.

There is no meaningful optimisation mod left for Forge 1.20.1 that this pack does not have.

**More Culling** was the one real candidate and it **has no Forge build** — Fabric, Quilt and
NeoForge only. It could be forced through Sinytra Connector, which the pack does ship, but a Fabric
chunk-rendering mod bolted onto Forge alongside Xenon is a crash risk with no measured upside. It was
not added.

Also ruled out, for the record: **Nvidium** (breaks under shaders, and the pack ships Oculus +
Complementary), and **Starlight / Canary / C2ME / Noisium / VMP** (Fabric-only, or they conflict with
Radium, which is already doing that job).

Adding more content mods will make all of this worse. That is not an argument against doing it — it
is an argument for doing it *after* a player has confirmed the out-of-memory kicks have stopped, so
there is a clean baseline to regress against.
