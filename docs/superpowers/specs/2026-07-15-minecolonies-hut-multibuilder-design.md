# MineColonies Hut-Based Multi-Builder — Design

**Date:** 2026-07-15
**Supersedes** the *user-facing* behaviour of the cross-hut split in `2026-07-14-minecolonies-multibuilder-design.md`. Keeps that work's reusable pieces (slice math, slice-filter predicate, DECORATE barrier concept, GUI selector, config keys); replaces its split mechanism (parent/child work orders across buildings) with per-citizen slices inside one hut.
**Target:** same fork `minecolonies-fork`, branch `feature/multibuilder`, on top of the committed cross-hut work.

## Goal

A single builder hut can employ multiple builders (scaling with hut level, hard max 16). When that hut constructs a building, its participating builders divide the blueprint into disjoint vertical slices — one per builder — so the build finishes faster. Materials flow through the normal per-citizen request system (warehouse + couriers); no special resource plumbing.

## Why the re-architecture

MineColonies stores a builder's live build state — `progressPos`, `progressStage` (and the building's needed-resources / requested flag) — on `AbstractBuildingStructureBuilder`, i.e. **per hut**, because a hut has always had exactly one builder. Multiple builders in one hut would clobber this shared state. The core change is to move that live build state, plus the slice assignment, to **per-citizen** storage.

## Model

- One work order per hut (unchanged). Up to K participating builder citizens share it.
- When the build begins, each participating citizen is assigned a slice: `(index, count K, axisX, start, end)` computed by `BuildSlicing` over the blueprint's longest horizontal axis — disjoint by construction, no overlap.
- Build progress, current stage, and slice-done flag are tracked **per citizen**.
- **DECORATE barrier:** a citizen that reaches DECORATE waits until every participating citizen has finished its solid stages, then proceeds (prevents attachable blocks popping at slice seams).
- **Completion:** the work order's stock completion (level-up, stats, events, chat) fires exactly once, when the **last** participating citizen finishes its slice.

## Hut worker capacity

- Builder `WorkerBuildingModule` max workers changes from constant `1` to `min(maxBuildersPerHut, round(maxBuildersPerHut × level ÷ maxLevel))` so a fully-upgraded hut reaches the cap and lower levels get proportionally fewer (always ≥ 1).
- New config `maxBuildersPerHut` (default 16, range 1..64). Setting 1 restores stock single-builder huts.

## Player control (reuse GUI selector)

- The existing build-window `Builders: [-] N [+]` selector stays. Its max becomes `min(current builders employed in the chosen hut, blueprint width)`; default 1 (stock behaviour). The value tells the hut how many of its builders to put on this build.

## Per-citizen state (the crux)

A recon step must enumerate every field the builder AI mutates on `AbstractBuildingStructureBuilder` during a build and re-home the per-build ones to a per-citizen structure keyed by citizen id (persisted in building NBT, since the hut owns the citizens). Known candidates: `progressPos`, `progressStage`, the slice assignment, the per-citizen solid-stage-done flag. Needed-resources and requests are already partly per-citizen in MineColonies (`getOpenRequestsOfCitizenOrBuilding`, `hasCitizenCompletedRequests`) — the recon confirms exactly what stays shared vs. moves.

## Assignment & reassignment

- When the hut has a work order and P = selector value, assign slices to `K = min(P, idle builders currently employed in the hut, blueprint width)` citizens.
- Assignment stored on the building keyed by citizen id; persisted in NBT; survives save/reload.
- A participating builder removed / dies / is fired mid-build: its slice returns to the unassigned pool and any idle hut builder repicks it, resuming from that slice's saved progress. If no builder is free, remaining builders still finish theirs; the barrier and completion account for the reduced set.

## Reuse vs replace (relative to the cross-hut commits)

- **Reuse:** `BuildSlicing` (Task 3), `outsideSlice()` predicate (Task 7) — sourced from the citizen's slice instead of the work order's, `canEnterDecorate` concept (Task 6/7) — across the hut's citizens instead of sibling work orders, GUI selector (Task 9), config keys (Task 4).
- **Replace / remove:** `WorkManager.addWorkOrderSplit` + parent/child work orders and their completion/cancel routing (Task 6), the work-order-level slice fields (Task 5) → per-citizen slice assignment. The network `builderCount` wiring (Task 8) is retargeted to drive per-citizen assignment rather than child-WO creation.

## Testing

Dev client: level one builder hut to employ 3+ builders, hire them, order a large build with the selector at 3. Verify: slices disjoint, union = full building (incl. rotated/mirrored); each builder requests its slice's materials once from the warehouse; DECORATE barrier holds; exactly one level-up; save/reload mid-build resumes per-citizen progress; firing one builder mid-build reassigns its slice; selector=1 is stock behaviour.
