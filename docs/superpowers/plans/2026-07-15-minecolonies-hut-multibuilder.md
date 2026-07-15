# Hut-Based Multi-Builder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** A builder hut employs multiple builders (scaling with level, max 16); when it builds, its builders split the blueprint into disjoint per-citizen slices. Warehouse resources.

**Architecture:** Move per-build live state (progress, stage, slice) from the hut (`AbstractBuildingStructureBuilder`, shared) to **per-citizen** storage keyed by citizen id. One work order per hut, K participating citizens each on a disjoint `BuildSlicing` slice, DECORATE barrier across the hut's citizens, single completion when the last finishes. Reuses `BuildSlicing`, the `outsideSlice()` predicate, the barrier concept, GUI selector, config; removes the cross-hut parent/child work-order machinery.

**Tech Stack:** Java 17, ForgeGradle (Forge 47.x, MC 1.20.1), MineColonies source, JUnit 5.

## Global Constraints

- Fork `c:\Minecraft-dev-workspace\minecolonies-fork`, branch `feature/multibuilder`, on top of current HEAD.
- Package prefix `com.minecolonies.core`.
- Gradle runs on JDK 17 (pinned globally in `~/.gradle/gradle.properties`); never set JAVA_HOME; `./gradlew compileJava` after every source change.
- `maxBuildersPerHut` config default 16, range 1..64; hut capacity `min(cap, round(cap*level/maxLevel))`, min 1.
- Selector default 1 (stock single-builder). Selector max = `min(builders employed in hut, blueprint width)`.
- Server clamps authoritatively; client selector is UX only.
- Reuse `BuildSlicing` (unchanged), config keys, GUI selector shell. Do not reintroduce a per-tick resource-request spike for non-split builds.
- Every patch to existing code anchors on symbols from Task 0's recon, with a compile check after.

---

### Task 0: Recon — per-citizen migration map

**Files:** Create `minecolonies-fork/docs/hut-multibuilder-symbols.md`

- [ ] **Step 1:** In the fork, locate and record real paths/symbols for: `AbstractBuildingStructureBuilder` every field the builder AI mutates during a build (`progressPos`, `progressStage`, needed-resources/`requested` handling, work-order handle) with the exact getter/setter names and every AI call site that reads/writes each; the `WorkerBuildingModule` capacity constructor arg (`BuildingModules.BUILDER_WORK`, line ~429) and how `getModuleMax`/assignment uses it; how a building with capacity>1 hires and schedules one AI per citizen (find a real multi-worker building, e.g. guard tower, and how per-citizen AI state is kept); the builder hiring GUI (assignment module view) and whether it already supports N slots; the completion path (`AbstractEntityAIStructureWithWorkOrder.executeSpecificCompleteActions` / `building.complete(citizen)`); where `getProgress()`/`setProgressPos()` are called across the AI; the current cross-hut split code to remove (`WorkManager.addWorkOrderSplit`, parent/child handling, WO slice fields in `AbstractWorkOrder`, `outsideSlice()` in the AI).
- [ ] **Step 2:** Write `docs/hut-multibuilder-symbols.md`: for each per-build field, its real name + every read/write site; the capacity mechanism; the hiring/assignment flow; the completion/progress call sites; a checklist of cross-hut code to remove and what to keep.
- [ ] **Step 3:** Commit `docs: hut-multibuilder symbol/migration map`.

---

### Task 1: Hut worker capacity

**Files:** Modify `BuildingModules.java` (BUILDER_WORK), `ServerConfiguration.java`.

**Interfaces:** Produces `maxBuildersPerHut` config; builder hut max workers = `min(cap, round(cap*level/maxLevel))`, min 1.

- [ ] **Step 1:** Add `maxBuildersPerHut = defineInteger(builder, "maxbuildersperhut", 16, 1, 64);` next to the other multi-builder config in `ServerConfiguration`.
- [ ] **Step 2:** Change `BUILDER_WORK`'s capacity lambda from `(b) -> 1` to a helper computing `Math.max(1, Math.min(cap, Math.round(cap * b.getBuildingLevel() / (float) b.getBuildingMaxLevel())))` where `cap = MineColonies.getConfig().getServer().maxBuildersPerHut.get()`.
- [ ] **Step 3:** `./gradlew compileJava` → SUCCESS.
- [ ] **Step 4:** Commit `feat: builder hut employs multiple builders scaling with level (max maxBuildersPerHut)`.

---

### Task 2: Per-citizen build state on the hut

**Files:** Modify `AbstractBuildingStructureBuilder.java` (+ NBT), any interface it implements.

**Interfaces:** Produces per-citizen accessors keyed by citizen id, replacing the shared `progressPos`/`progressStage` for the AI's use:
- `getProgress(int citizenId)` / `setProgressPos(int citizenId, BlockPos, BuildingProgressStage)`
- `getSlice(int citizenId)` returning `(index,count,axisX,start,end)` or absent; `assignSlice(int citizenId, ...)`; `clearAssignments()`
- `isSolidStageDone(int citizenId)` / `setSolidStageDone(int citizenId, boolean)`
- `getParticipatingCitizens()` (ids with a slice on the current WO)
- All persisted in the building's NBT (map keyed by citizen id) and cleared on work-order completion/cancel.

- [ ] **Step 1:** Add a per-citizen build-state record (slice fields + progressPos + progressStage + solidDone) stored in a `Map<Integer, ...>` on the building. Migrate the AI's progress read/writes (per Task 0 call-site list) to the citizen-keyed accessors; keep the old shared field only if a non-builder path still needs it (recon decides), else remove.
- [ ] **Step 2:** NBT: write/read the map; default empty (old saves and non-split builds load fine — an empty map means "no per-citizen split", AI falls back to whole-structure single-builder behaviour for a lone citizen).
- [ ] **Step 3:** `./gradlew compileJava` → SUCCESS.
- [ ] **Step 4:** Commit `feat: per-citizen build progress/stage/slice state on builder hut`.

---

### Task 3: Slice assignment when a build starts

**Files:** Modify `AbstractBuildingStructureBuilder` / the point where a builder claims/starts the WO; use `BuildSlicing` + config; consume the selector count (Task 6).

**Interfaces:** When the hut's WO is set and P builders requested, assign `K = min(P, idle employed builders, blueprint width)` slices via `BuildSlicing.sliceStart/End`, axis = `max(sizeX,sizeZ)`; store per citizen (Task 2). K=1 → no split (single citizen builds whole thing, stock path).

- [ ] **Step 1:** On build start (WO claimed by the hut, blueprint resolved), compute K and assign slices to K of the hut's idle builders; store assignment. Recompute/repick on a builder becoming free mid-build if unassigned slices remain.
- [ ] **Step 2:** `./gradlew compileJava` → SUCCESS.
- [ ] **Step 3:** Commit `feat: assign disjoint slices to a hut's builders on build start`.

---

### Task 4: Builder AI — per-citizen slice filter + barrier + completion

**Files:** Modify `AbstractEntityAIStructure.java`, `AbstractEntityAIStructureWithWorkOrder.java`.

**Interfaces:** Consumes Task 2/3. `outsideSlice()` sources the slice from `building.getSlice(worker.getCitizenData().getId())`. Barrier: before DECORATE, set this citizen's solidDone and idle until all participating citizens done. Completion: WO stock completion fires once when the last participating citizen finishes; earlier finishers just idle/return.

- [ ] **Step 1:** Retarget `outsideSlice()` to the citizen's slice (was the WO's). Retarget the material-request single-wave loop and the DECORATE barrier to per-citizen state. Route the whole-build completion actions to fire once on the last participating citizen (reuse the Task-6 extracted `triggerCompletion*` statics, gated on "am I the last").
- [ ] **Step 2:** `./gradlew compileJava` → SUCCESS.
- [ ] **Step 3:** Commit `feat: builder AI builds its own per-citizen slice with barrier and single completion`.

---

### Task 5: Remove cross-hut child-work-order machinery

**Files:** Modify `WorkManager.java`, `AbstractWorkOrder.java`, `IWorkOrder.java`, message handlers, view classes.

**Interfaces:** Removes `addWorkOrderSplit`, parent/child WO fields + routing, WO-level slice fields + view sync, dedup bypass, cancel/completion child routing — everything the per-citizen model replaces. Keep: `BuildSlicing`, config, GUI selector, the network `builderCount` field (now drives Task 3 assignment).

- [ ] **Step 1:** Remove the child-WO code per Task 0's removal checklist; retarget the request-message `builderCount` to set the hut's requested builder count (consumed by Task 3) instead of calling `addWorkOrderSplit`. Ensure non-split path is byte-identical to stock.
- [ ] **Step 2:** `./gradlew compileJava` → SUCCESS; `./gradlew test --tests "com.minecolonies.core.colony.workorders.BuildSlicingTest"` → SUCCESS.
- [ ] **Step 3:** Commit `refactor: remove cross-hut child work orders, superseded by per-citizen hut slices`.

---

### Task 6: GUI selector cap by hut builders + width

**Files:** Modify `WindowBuildBuilding.java`, `WindowBuildDecoration.java`.

**Interfaces:** Selector max = `min(builders employed in the chosen hut, blueprint width)`; value flows to the request message → hut requested-count.

- [ ] **Step 1:** Compute the chosen hut's current builder count client-side (from the builder dropdown selection / building view) and cap the selector at `min(that, blueprintWidth)`. Keep width from Task-9 blueprint callback.
- [ ] **Step 2:** `./gradlew compileJava` → SUCCESS.
- [ ] **Step 3:** Commit `feat: cap builder selector by employed builders and blueprint width`.

---

### Task 7: Dev integration test

- [ ] Level a builder hut to employ 3+ builders; hire them; order a large build with selector 3. Verify: 3 disjoint slices, union = whole building (rotated + mirrored); each requests its slice's materials once from a warehouse; DECORATE barrier holds; one level-up; save/reload resumes per-citizen progress; fire one builder mid-build → slice reassigned; selector=1 identical to stock. Fix-and-commit loop for any bug.

---

### Task 8: Package + deploy

- [ ] Rebuild the release jar (`gradlew assemble`), keep deps aligned to pack runtime (structurize 816 / blockui 194 / domum 303), version-stamp. Stage on a world copy, then deploy to `Modpacks/server/run/mods/` + packwiz on the human's explicit go-ahead (all players must update).
