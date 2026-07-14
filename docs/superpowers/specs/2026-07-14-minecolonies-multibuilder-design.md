# MineColonies Multi-Builder Parallel Construction — Design

**Date:** 2026-07-14
**Target:** Patched fork of MineColonies `1.20.1-1.1.1255` (Forge 1.20.1), replacing the stock jar in this modpack on server and clients.
**Goal:** A player ordering a build can choose how many builders (N) work on that one structure. The blueprint is divided into N disjoint slices, one per builder, so the build finishes faster with no overlap between builders.

## Background

Stock MineColonies: one work order is claimed by exactly one builder, who places every block of the blueprint sequentially via the Structurize placer. Multiple builders only parallelize across different buildings. No config or feature exists for splitting one build. This workspace contains only the compiled jar (`Modpacks/server/run/mods/minecolonies-1.20.1-1.1.1255-snapshot.jar`), so the feature requires forking the MineColonies source (`ldtteam/minecolonies`, branch `version/1.20.1`, commit matching 1.1.1255), patching, and building a replacement jar.

Approaches considered:
- **Fork + patch (chosen):** direct changes to work-order and builder-AI code; smallest total complexity; requires re-patching on upstream updates (kept as a small commit series for easy rebase).
- **Mixin addon mod:** rejected — mixin targets sit deep in AI internals, more fragile and more total work.
- **Config/existing feature:** rejected — feature does not exist stock.

## 1. Player control (UI)

- **Build Options window** (hut block GUI → build/upgrade/repair): new selector row `Builders: [-] N [+]`, range 1 to `min(maxBuildersPerBuild, eligible builder count)`. Default 1 (vanilla behavior).
- Same selector in the decoration build window.
- The existing builder dropdown stays. If the player picks a specific builder, that builder takes the first slice; the remaining N-1 are auto-assigned from idle eligible builders.
- N travels to the server in the existing build-request network message (new int field). Server clamps N against the cap, eligibility, and `minSliceWidth` — client input is never trusted.

## 2. Splitting model

- Applies to work order types: building build, upgrade, repair, and decoration. Miner shafts and other non-blueprint work are out of scope.
- Eligible builder: idle (no claimed work order), hut level sufficient for the target build level, in the same colony.
- Effective split count: `k = min(requestedN, maxBuildersPerBuild, eligibleCount, floor(blueprintWidthAlongSplitAxis / minSliceWidth))`. If `k == 1`, the stock single-builder path runs untouched.
- The blueprint is sliced into `k` vertical slabs along its longest horizontal axis. Each slice is a contiguous range of full-height columns — disjoint by construction, so builders can never overlap and no locking is needed.
- A **parent work order** remains as bookkeeping and spawns `k` **child work orders**, each carrying `sliceIndex`, `sliceCount`, and the same blueprint reference.

## 3. Builder AI changes

- Each child work order is claimed by one builder through the existing claim logic, unchanged.
- In the structure iteration (`AbstractEntityAIStructureWithWorkOrder` → Structurize placer loop), each builder skips any block position whose local coordinate along the split axis falls outside its slice range. This is a per-position predicate; the existing per-builder progress save/resume keeps working.
- Each builder issues material requests only for blocks in its own slice; the existing per-builder request system handles this naturally.

## 4. Stage barrier

- Stock stages per builder: CLEAR → SOLID → DECORATE → SPAWN.
- Rule: no child may enter DECORATE until **all** siblings have finished SOLID. This prevents attachable blocks (torches, ladders, banners) at slice boundaries from being placed against not-yet-built support and popping off.
- Implementation: each child reports its stage to the parent work order; a builder whose siblings are still behind idles at the gate until released.

## 5. Completion and failure handling

- A child completing notifies the parent. When all children are done, the parent fires the stock completion exactly once (building level-up, citizen XP, etc.). Children never fire completion side effects.
- If a builder is cancelled, dies, or is reassigned mid-slice, its child work order unclaims and returns to the queue; any eligible builder can pick it up and resume from the saved progress — reusing stock behavior.
- Cancelling the parent build cancels all children.

## 6. Config

In `minecolonies-server.toml`:
- `maxBuildersPerBuild` — server-side cap on the count players may pick. Default 4. Setting 1 disables the feature entirely.
- `minSliceWidth` — minimum slice width in blocks along the split axis. Default 5. Prevents tiny builds from splitting.

## 7. Deployment and compatibility

- The patched jar must replace the stock jar on **the server and every client** (work-order sync and the build-request message are networked). The packwiz mod entry is updated so players receive it on their next update run.
- New NBT fields and the child work-order type are additive: existing worlds and in-flight stock work orders load unchanged.
- **Downgrade risk:** if the patched jar is later removed while child work orders are active, those orders are dropped. Cancel active multi-builder builds before any downgrade.
- Upstream MineColonies updates require re-patching: the fork keeps the feature as a small commit series to rebase onto new tags.

## 8. Testing

1. Gradle `runClient`/`runServer` dev instance: colony with 3+ builders; order a large build (e.g. high-level town hall) with N=3.
2. Verify: slices assigned without overlap; DECORATE barrier holds; materials requested per builder; exactly one level-up on completion; save/reload mid-build resumes correctly; builder death mid-slice → slice resumed by another builder; N=1 identical to stock.
3. Stage the jar on a copy of the real world before deploying to the live server.
