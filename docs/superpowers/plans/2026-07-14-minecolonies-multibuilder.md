# MineColonies Multi-Builder Parallel Construction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fork MineColonies 1.20.1-1.1.1255, add player-selectable N-builder parallel construction where one build order splits into N disjoint vertical slices, one per builder.

**Architecture:** Slice fields live directly on `AbstractWorkOrder` (no new work-order subclasses). A parent work order spawns N child work orders (same class, `sliceCount > 1`, `parentId` set); the parent becomes unclaimable bookkeeping. The builder AI filters blueprint positions by slice range via an extra skip-predicate in the existing Structurize iterator calls, and a stage barrier blocks DECORATE until all siblings finish solid stages. The build-request GUI gains a `[-] N [+]` selector whose value travels in the existing build-request network message.

**Tech Stack:** Java 17, ForgeGradle (Forge 47.x for MC 1.20.1), MineColonies source (`ldtteam/minecolonies`, branch `version/1.20.1`), JUnit 5 for pure-logic tests, packwiz for modpack deployment.

## Global Constraints

- Base version: MineColonies `1.20.1-1.1.1255-snapshot` (match the commit of the installed jar as closely as possible).
- Minecraft 1.20.1, Forge 47.x, JDK 17.
- Patched jar must be installed on **server and all clients** (networked changes).
- Config: `maxBuildersPerBuild` default **4** (server-side cap; 1 disables feature), `minSliceWidth` default **5** blocks.
- GUI selector default is **1** (vanilla behavior unless the player raises it).
- Server never trusts client N: clamp with `min(requestedN, maxBuildersPerBuild, eligibleCount, floor(width / minSliceWidth))`.
- Split applies to work orders for building build/upgrade/repair and decorations. Miner shafts and other non-blueprint work: out of scope.
- All fork work on branch `feature/multibuilder` in the fork clone at `c:\Minecraft-dev-workspace\minecolonies-fork`.
- Package prefix: this plan writes `com.minecolonies.core.*`. If the cloned source still uses `com.minecolonies.coremod.*`, substitute `coremod` for `core` in every path and import below (Task 2 records which one is real).
- Upstream file contents in this plan are reconstructed from API knowledge. Every patch step to an existing file anchors on symbol names, not line numbers, and every such step ends with a compile check. If an anchor symbol doesn't exist, STOP, re-check the symbol map from Task 2, and adapt the patch to the real method — do not guess-place code.

---

### Task 1: Toolchain, clone, version pin, baseline build

**Files:**
- Create: `c:\Minecraft-dev-workspace\minecolonies-fork\` (git clone)

**Interfaces:**
- Produces: a fork clone on branch `feature/multibuilder` that builds a jar via `gradlew assemble` (baseline, unmodified).

- [ ] **Step 1: Verify JDK 17**

Run: `java -version`
Expected: `openjdk version "17..."` or similar 17.x. If missing or wrong major: install Temurin 17 (`winget install EclipseAdoptium.Temurin.17.JDK`) and ensure `JAVA_HOME` points at it before continuing.

- [ ] **Step 2: Identify the exact upstream commit of the installed jar**

The installed jar is `c:\Minecraft-dev-workspace\Modpacks\server\run\mods\minecolonies-1.20.1-1.1.1255-snapshot.jar`. Extract its metadata:

```powershell
Expand-Archive -Path "c:\Minecraft-dev-workspace\Modpacks\server\run\mods\minecolonies-1.20.1-1.1.1255-snapshot.jar" -DestinationPath "$env:TEMP\mcjar" -Force
Get-Content "$env:TEMP\mcjar\META-INF\MANIFEST.MF"
Get-Content "$env:TEMP\mcjar\META-INF\mods.toml" | Select-String -Pattern "version"
```

Look for a git commit hash or build-number attribute in MANIFEST.MF. Record whatever version/commit info is found.

- [ ] **Step 3: Clone and check out the matching source**

```powershell
git clone --branch version/1.20.1 https://github.com/ldtteam/minecolonies.git c:\Minecraft-dev-workspace\minecolonies-fork
cd c:\Minecraft-dev-workspace\minecolonies-fork
git tag -l "*1255*"
git log --oneline -20
```

Priority order for the base commit:
1. A tag containing `1255` → check it out.
2. A commit hash found in Step 2's MANIFEST → check it out.
3. Otherwise: search `git log` for the commit whose CI build produced 1255 (ldtteam version numbers increment per build; check `gradle.properties` history if it pins a version). If nothing conclusive, use the branch head and record the delta risk in the task report — a slightly newer base is acceptable; the deployed jar simply becomes a newer feature build.

Then: `git checkout -b feature/multibuilder`

- [ ] **Step 4: Baseline build**

```powershell
cd c:\Minecraft-dev-workspace\minecolonies-fork
.\gradlew assemble --no-daemon
```

Expected: BUILD SUCCESSFUL; a jar appears under `build/libs/`. First run downloads dependencies and decompiles Forge — 5–20 minutes is normal. If the build fails on missing sibling deps (Structurize/BlockUI/domum-ornamentum), they come from ldtteam's maven via the project's own `build.gradle` — check network/proxy before touching build scripts.

- [ ] **Step 5: Record baseline**

Run: `ls build/libs/` and record the produced jar name and size. No commit needed (no changes yet).

---

### Task 2: Reconnaissance — verify the symbol map

**Files:**
- Create: `c:\Minecraft-dev-workspace\minecolonies-fork\docs\multibuilder-symbols.md`

**Interfaces:**
- Produces: `docs/multibuilder-symbols.md` in the fork — the verified list of real paths/symbols that every later task's patches anchor to. Later tasks reference symbols; this file resolves them to files.

- [ ] **Step 1: Locate each symbol**

For each row below, find the real file with Grep/Glob in the fork and record path + the relevant method/field names actually present:

| Symbol to find | Expected location (verify!) |
|---|---|
| `AbstractWorkOrder` (fields, NBT read/write, `serializeViewNetworkData`) | `src/main/java/com/minecolonies/core/colony/workorders/AbstractWorkOrder.java` |
| `WorkOrderBuilding`, `WorkOrderDecoration` | same package |
| `IWorkOrder` | `src/main/java/com/minecolonies/api/colony/workorders/IWorkOrder.java` |
| `WorkManager` (`addWorkOrder`, claim/assignment logic, `onWorkOrderCompleted` or equivalent) | `src/main/java/com/minecolonies/core/colony/workorders/WorkManager.java` |
| Builder claim path — where an idle builder picks an unclaimed work order (search usages of `setClaimedBy`) | `BuildingBuilder` / `AbstractBuildingStructureBuilder` / `WorkManager` |
| `AbstractEntityAIStructureWithWorkOrder` and `AbstractEntityAIStructure` (the `executeStructureStep` / iterator `increment(...)` call sites, stage switching) | `src/main/java/com/minecolonies/core/entity/ai/workers/...` (older layout: `entity/ai/basic/`) |
| `BuildingStructureHandler` + its `Stage` enum (exact stage list and order) | `src/main/java/com/minecolonies/core/entity/ai/workers/util/` or `util/` |
| Build request network message (search `class BuildRequestMessage` or usages from the build window) | `src/main/java/com/minecolonies/core/network/messages/server/colony/building/` |
| Decoration build message (`DecorationBuildRequestMessage` or similar) | `src/main/java/com/minecolonies/core/network/messages/server/` |
| `WindowBuildBuilding` (button handlers, how it sends the message) | `src/main/java/com/minecolonies/core/client/gui/` |
| `windowbuildbuilding.xml` (blockui layout) | `src/main/resources/assets/minecolonies/gui/` |
| Decoration controller window + its xml | same areas |
| Server config class (search `maxBlocksCheckedByBuilder` or `ServerConfiguration`) | `src/main/java/com/minecolonies/api/configuration/` or `core/...` |
| Network message registration (search `registerMessage` / `Network.getNetwork()`) | `src/main/java/com/minecolonies/core/network/NetworkChannel.java` or similar |
| Blueprint local-position info type passed to iterator skip predicates (`BlueprintPositionInfo`, its `getPos()`) | structurize dependency — find import in AI classes |
| Blueprint size accessors (`getSizeX()`/`getSizeZ()` on `Blueprint`) | structurize dependency — verify via usage in fork |
| Where a work order learns blueprint dimensions server-side at creation time (search `getSizeX` usages near work-order creation, or `StructurePacks` blueprint load) | record best hook |

- [ ] **Step 2: Write the symbol map**

Write `docs/multibuilder-symbols.md` in the fork: one row per symbol → real path, real package prefix (`core` vs `coremod`), the exact names of: the NBT read/write methods on `AbstractWorkOrder`, the claim method, the stage enum constants in real order, the message-registration call pattern, and the exact signature of the `increment(...)` skip-predicate call sites (copy 2–3 real call sites verbatim into the doc).

- [ ] **Step 3: Commit**

```bash
cd c:\Minecraft-dev-workspace\minecolonies-fork
git add docs/multibuilder-symbols.md
git commit -m "docs: multibuilder symbol map (verified upstream anchors)"
```

---

### Task 3: Slice math utility (pure logic, TDD)

**Files:**
- Create: `src/main/java/com/minecolonies/core/colony/workorders/BuildSlicing.java`
- Test: `src/test/java/com/minecolonies/core/colony/workorders/BuildSlicingTest.java`
- Possibly modify: `build.gradle` (JUnit 5 test deps, only if absent)

**Interfaces:**
- Produces:
  - `BuildSlicing.computeSliceCount(int requested, int cap, int eligible, int widthAlongAxis, int minSliceWidth) -> int`
  - `BuildSlicing.sliceStart(int index, int count, int size) -> int` and `BuildSlicing.sliceEnd(int index, int count, int size) -> int` (end exclusive; slices cover `[0, size)` exactly, no gaps/overlap)

- [ ] **Step 1: Ensure JUnit 5 wiring**

Check `build.gradle` for a `test` configuration. If missing, add:

```groovy
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
}
test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write the failing test**

`src/test/java/com/minecolonies/core/colony/workorders/BuildSlicingTest.java`:

```java
package com.minecolonies.core.colony.workorders;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BuildSlicingTest
{
    @Test
    public void sliceCountClampedByAllLimits()
    {
        // requested 4, cap 4, 4 eligible, width 40, min 5 -> 4
        assertEquals(4, BuildSlicing.computeSliceCount(4, 4, 4, 40, 5));
        // cap wins
        assertEquals(2, BuildSlicing.computeSliceCount(4, 2, 4, 40, 5));
        // eligibility wins
        assertEquals(3, BuildSlicing.computeSliceCount(4, 4, 3, 40, 5));
        // width wins: floor(12/5) = 2
        assertEquals(2, BuildSlicing.computeSliceCount(4, 4, 4, 12, 5));
        // never below 1, even with garbage inputs
        assertEquals(1, BuildSlicing.computeSliceCount(0, 4, 4, 40, 5));
        assertEquals(1, BuildSlicing.computeSliceCount(4, 4, 0, 40, 5));
        assertEquals(1, BuildSlicing.computeSliceCount(4, 4, 4, 3, 5));
    }

    @Test
    public void slicesTileTheAxisExactly()
    {
        final int size = 37;
        final int count = 4;
        int covered = 0;
        int prevEnd = 0;
        for (int i = 0; i < count; i++)
        {
            final int start = BuildSlicing.sliceStart(i, count, size);
            final int end = BuildSlicing.sliceEnd(i, count, size);
            assertEquals(prevEnd, start, "slice " + i + " must start where previous ended");
            assertTrue(end > start, "slice " + i + " must be non-empty");
            covered += end - start;
            prevEnd = end;
        }
        assertEquals(size, covered);
        assertEquals(size, prevEnd);
    }

    @Test
    public void singleSliceCoversEverything()
    {
        assertEquals(0, BuildSlicing.sliceStart(0, 1, 25));
        assertEquals(25, BuildSlicing.sliceEnd(0, 1, 25));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `.\gradlew test --tests "com.minecolonies.core.colony.workorders.BuildSlicingTest"`
Expected: FAIL — `BuildSlicing` does not exist (compile error).

- [ ] **Step 4: Implement**

`src/main/java/com/minecolonies/core/colony/workorders/BuildSlicing.java`:

```java
package com.minecolonies.core.colony.workorders;

/**
 * Pure math for dividing a blueprint axis into disjoint builder slices.
 * Slices are contiguous [start, end) column ranges that tile [0, size) exactly.
 */
public final class BuildSlicing
{
    private BuildSlicing() {}

    /**
     * Effective number of slices after applying every server-side clamp.
     * Always at least 1.
     */
    public static int computeSliceCount(final int requested, final int cap, final int eligible, final int widthAlongAxis, final int minSliceWidth)
    {
        final int byWidth = minSliceWidth <= 0 ? requested : widthAlongAxis / minSliceWidth;
        final int k = Math.min(Math.min(requested, cap), Math.min(eligible, byWidth));
        return Math.max(1, k);
    }

    /** Inclusive start of slice {@code index} of {@code count} over an axis of {@code size} blocks. */
    public static int sliceStart(final int index, final int count, final int size)
    {
        return (int) ((long) index * size / count);
    }

    /** Exclusive end of slice {@code index} of {@code count} over an axis of {@code size} blocks. */
    public static int sliceEnd(final int index, final int count, final int size)
    {
        return (int) ((long) (index + 1) * size / count);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `.\gradlew test --tests "com.minecolonies.core.colony.workorders.BuildSlicingTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/minecolonies/core/colony/workorders/BuildSlicing.java src/test/java/com/minecolonies/core/colony/workorders/BuildSlicingTest.java build.gradle
git commit -m "feat: slice math for multi-builder work orders (TDD)"
```

---

### Task 4: Config entries

**Files:**
- Modify: server config class found in Task 2 (expected `src/main/java/com/minecolonies/api/configuration/ServerConfiguration.java`)

**Interfaces:**
- Produces: `ServerConfiguration.maxBuildersPerBuild` (IntValue, default 4, range 1..16) and `ServerConfiguration.minSliceWidth` (IntValue, default 5, range 1..64), read via the mod's standard config accessor (as recorded in the symbol map, e.g. `MineColonies.getConfig().getServer()`).

- [ ] **Step 1: Add config fields**

In the server configuration class, follow the existing `defineInteger`/builder pattern exactly as neighboring entries do. Expected shape (adapt to the file's actual helper methods):

```java
public final ForgeConfigSpec.IntValue maxBuildersPerBuild;
public final ForgeConfigSpec.IntValue minSliceWidth;
```

and in the constructor, next to other builder-related settings:

```java
maxBuildersPerBuild = defineInteger(builder, "maxbuildersperbuild", 4, 1, 16);
minSliceWidth = defineInteger(builder, "minslicewidth", 5, 1, 64);
```

If the class uses a raw `builder.comment(...).defineInRange(name, def, min, max)` pattern instead, mirror that pattern verbatim.

- [ ] **Step 2: Compile check**

Run: `.\gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A src/main/java
git commit -m "feat: server config for multi-builder (maxBuildersPerBuild, minSliceWidth)"
```

---

### Task 5: Slice + parent/child fields on AbstractWorkOrder

**Files:**
- Modify: `src/main/java/com/minecolonies/core/colony/workorders/AbstractWorkOrder.java`
- Modify: `src/main/java/com/minecolonies/api/colony/workorders/IWorkOrder.java`

**Interfaces:**
- Produces (on `IWorkOrder` / `AbstractWorkOrder`):
  - `int getSliceIndex()` / `int getSliceCount()` (count 1 = not sliced)
  - `boolean isSliceAxisX()`
  - `int getSliceStart()` / `int getSliceEnd()` (local blueprint coords along split axis, end exclusive)
  - `int getParentId()` (0 = none) / `java.util.List<Integer> getChildIds()`
  - `boolean isMultiBuildParent()` (childIds non-empty)
  - `boolean isSolidStageDone()` / `void setSolidStageDone(boolean)`
  - `void setSlice(int index, int count, boolean axisX, int start, int end)` / `void setParentId(int id)` / `void addChildId(int id)`
  - All fields persisted in NBT read/write and included in the view/network serialization alongside existing fields.

- [ ] **Step 1: Add fields + accessors**

In `AbstractWorkOrder`, next to existing fields:

```java
private int sliceIndex = 0;
private int sliceCount = 1;
private boolean sliceAxisX = true;
private int sliceStart = 0;
private int sliceEnd = 0;
private int parentId = 0;
private final java.util.List<Integer> childIds = new java.util.ArrayList<>();
private boolean solidStageDone = false;
```

Accessors (plain getters/setters exactly as listed in Interfaces above; `isMultiBuildParent()` returns `!childIds.isEmpty()`). Add the getter signatures to `IWorkOrder` so colony/AI code can use them through the interface.

- [ ] **Step 2: Persist in NBT**

In the class's NBT write method (name per symbol map, e.g. `serializeNBT`/`write`), append:

```java
compound.putInt("sliceIndex", sliceIndex);
compound.putInt("sliceCount", sliceCount);
compound.putBoolean("sliceAxisX", sliceAxisX);
compound.putInt("sliceStart", sliceStart);
compound.putInt("sliceEnd", sliceEnd);
compound.putInt("mbParent", parentId);
compound.putIntArray("mbChildren", childIds.stream().mapToInt(Integer::intValue).toArray());
compound.putBoolean("mbSolidDone", solidStageDone);
```

In the NBT read method (defaults keep old saves loading unchanged):

```java
sliceIndex = compound.getInt("sliceIndex");
sliceCount = compound.contains("sliceCount") ? compound.getInt("sliceCount") : 1;
sliceAxisX = !compound.contains("sliceAxisX") || compound.getBoolean("sliceAxisX");
sliceStart = compound.getInt("sliceStart");
sliceEnd = compound.getInt("sliceEnd");
parentId = compound.getInt("mbParent");
childIds.clear();
for (final int id : compound.getIntArray("mbChildren")) { childIds.add(id); }
solidStageDone = compound.getBoolean("mbSolidDone");
```

- [ ] **Step 3: Include in view serialization**

In the buffer write method used for client sync (per symbol map, e.g. `serializeViewNetworkData(FriendlyByteBuf buf)`), append in the same order on write and read sides:

```java
buf.writeInt(sliceIndex);
buf.writeInt(sliceCount);
```

(Only index/count are needed client-side, for display; keep the rest server-only.) Locate the paired view-read (`WorkOrderView` or the view class's deserialize) and read the two ints in the same position.

- [ ] **Step 4: Compile check**

Run: `.\gradlew compileJava`
Expected: BUILD SUCCESSFUL. If the view read/write pairing is asymmetric, the dev client will desync later — double-check both sides byte-for-byte now.

- [ ] **Step 5: Commit**

```bash
git add -A src/main/java
git commit -m "feat: slice and parent/child fields on work orders (NBT + view sync)"
```

---

### Task 6: WorkManager — split on add, parent bookkeeping, completion, cancel

**Files:**
- Modify: `src/main/java/com/minecolonies/core/colony/workorders/WorkManager.java`
- Modify: claim-eligibility site(s) found in Task 2 (where builders pick unclaimed work orders — expected `AbstractBuildingStructureBuilder.searchWorkOrder()` or equivalent)

**Interfaces:**
- Consumes: `BuildSlicing` (Task 3), slice fields (Task 5), config (Task 4).
- Produces:
  - `WorkManager.addWorkOrderSplit(IWorkOrder order, int requestedBuilders)` — public entry the request messages call (Task 8). Splits into children when clamped k > 1, else falls through to stock `addWorkOrder`.
  - Parent work orders are never claimable; children are.
  - Child completion → parent completes exactly once when all children done; parent cancel → children cancelled.
  - `WorkManager.canEnterDecorate(IWorkOrder child) -> boolean` for the AI barrier (Task 7).

- [ ] **Step 1: Implement split-on-add**

Add to `WorkManager` (adapt collection/ID allocation to the file's real patterns — it already assigns incrementing work-order IDs in `addWorkOrder`):

```java
/**
 * Add a work order, splitting it into disjoint slice children when the player
 * requested more than one builder. Parent stays as unclaimable bookkeeping.
 */
public void addWorkOrderSplit(final IWorkOrder order, final int requestedBuilders)
{
    final int cap = MineColonies.getConfig().getServer().maxBuildersPerBuild.get();
    final int minWidth = MineColonies.getConfig().getServer().minSliceWidth.get();

    final Blueprint blueprint = loadBlueprintFor(order); // per symbol map: the same load call the AI/WO uses
    if (blueprint == null || requestedBuilders <= 1 || cap <= 1)
    {
        addWorkOrder(order, false);
        return;
    }

    final boolean axisX = blueprint.getSizeX() >= blueprint.getSizeZ();
    final int width = axisX ? blueprint.getSizeX() : blueprint.getSizeZ();
    final int eligible = countEligibleBuilders(order); // Step 2
    final int k = BuildSlicing.computeSliceCount(requestedBuilders, cap, eligible, width, minWidth);

    if (k <= 1)
    {
        addWorkOrder(order, false);
        return;
    }

    addWorkOrder(order, false); // parent gets its ID via the stock path
    for (int i = 0; i < k; i++)
    {
        final IWorkOrder child = order.copyForSlice(); // Step 3
        child.setSlice(i, k, axisX, BuildSlicing.sliceStart(i, k, width), BuildSlicing.sliceEnd(i, k, width));
        child.setParentId(order.getID());
        addWorkOrder(child, false);
        order.addChildId(child.getID());
    }
}
```

`loadBlueprintFor` / `countEligibleBuilders`: implement using the exact calls recorded in the symbol map (blueprint load is the same call the work order/AI already uses to resolve its structure; eligible = colony's builder buildings that have no claimed work order and whose building level permits the target level — reuse the same level check the stock claim path applies, referenced from the claim site found in Task 2).

- [ ] **Step 2: Implement `countEligibleBuilders`**

Mirror the stock claim eligibility exactly (copy its conditions; do not invent new ones). Count, don't assign — assignment stays with the stock claim loop.

- [ ] **Step 3: Implement `copyForSlice` on work orders**

On `AbstractWorkOrder` (and exposed via `IWorkOrder`): serialize this work order to NBT with the existing write method, construct a fresh instance of the same class via the existing NBT factory (`AbstractWorkOrder.createFromNBT` per symbol map), clear its ID so `addWorkOrder` assigns a new one, clear `childIds`, reset `claimedBy`, reset `solidStageDone`. This reuses stock serialization instead of a field-by-field copy constructor.

```java
@Override
public IWorkOrder copyForSlice()
{
    final CompoundTag tag = new CompoundTag();
    this.write(tag); // real write method name per symbol map
    final IWorkOrder copy = AbstractWorkOrder.createFromNBT(tag, manager); // real factory per symbol map
    copy.resetForSliceChild(); // clears id/claim/children/solidDone — add this small method alongside
    return copy;
}
```

- [ ] **Step 4: Make parents unclaimable**

At the claim site(s) from Task 2 (builder searching for open work orders), add to the existing filter conditions:

```java
&& !workOrder.isMultiBuildParent()
```

- [ ] **Step 5: Completion + cancel wiring**

In `WorkManager`'s work-order-completed path (symbol map: where a finished WO is removed / `onCompleted` fires):

```java
if (order.getParentId() != 0)
{
    final IWorkOrder parent = getWorkOrder(order.getParentId());
    removeWorkOrder(order, false); // child leaves silently: no completion side effects
    if (parent != null)
    {
        parent.getChildIds().remove(Integer.valueOf(order.getID()));
        if (parent.getChildIds().isEmpty())
        {
            completeWorkOrder(parent); // stock completion path: level-up etc., exactly once
        }
    }
    return;
}
```

Adapt `removeWorkOrder`/`completeWorkOrder` names to the file's real ones; the critical property: **children must skip the stock completion side effects** (building level-up), the parent must go through them. Check what the stock completion path does (per symbol map) and route child removal around it.

In the cancel path (user cancels via town hall / builder reassigned): if a parent is cancelled, iterate a copy of `childIds` and cancel each child through the same stock cancel routine; if a child is cancelled by the *system* (builder died) it just unclaims per stock behavior — do not touch parent.

- [ ] **Step 6: Barrier query**

```java
/** True when every sibling slice (including the asker) finished its solid stages. */
public boolean canEnterDecorate(final IWorkOrder child)
{
    if (child.getParentId() == 0) { return true; }
    final IWorkOrder parent = getWorkOrder(child.getParentId());
    if (parent == null) { return true; }
    for (final Integer id : parent.getChildIds())
    {
        final IWorkOrder sibling = getWorkOrder(id);
        if (sibling != null && !sibling.isSolidStageDone()) { return false; }
    }
    return true;
}
```

- [ ] **Step 7: Compile check**

Run: `.\gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add -A src/main/java
git commit -m "feat: work manager split-on-add, parent bookkeeping, completion and cancel wiring"
```

---

### Task 7: Builder AI — slice filter + DECORATE barrier

**Files:**
- Modify: `src/main/java/com/minecolonies/core/entity/ai/workers/AbstractEntityAIStructureWithWorkOrder.java` (real path per symbol map)
- Modify: `AbstractEntityAIStructure` iterator call sites (same package; real file per symbol map)

**Interfaces:**
- Consumes: slice fields (Task 5), `WorkManager.canEnterDecorate` (Task 6).
- Produces: builders skip out-of-slice positions in every structure stage; builders idle before DECORATE until siblings finish solid stages; `solidStageDone` set when a child first reaches the DECORATE gate.

- [ ] **Step 1: Add the slice predicate**

In the AI class that owns the work order (`AbstractEntityAIStructureWithWorkOrder`), add:

```java
/**
 * Skip predicate: true when this blueprint-local position lies OUTSIDE this
 * builder's slice. Composed into the existing iterator skip conditions.
 */
protected java.util.function.Predicate<BlueprintPositionInfo> outsideSlice()
{
    final IWorkOrder wo = getWorkOrder(); // real accessor per symbol map
    if (wo == null || wo.getSliceCount() <= 1)
    {
        return info -> false;
    }
    final boolean axisX = wo.isSliceAxisX();
    final int start = wo.getSliceStart();
    final int end = wo.getSliceEnd();
    return info -> {
        final int c = axisX ? info.getPos().getX() : info.getPos().getZ();
        return c < start || c >= end;
    };
}
```

- [ ] **Step 2: Compose into every iterator call site**

Task 2's symbol map lists the verbatim `placer.executeStructureStep(..., () -> placer.getIterator().increment(<predicates>), ...)` call sites (clear, solid, decorate, water removal — every stage that iterates blueprint positions). At each, wrap the existing predicate:

```java
// before
() -> placer.getIterator().increment(EXISTING_PREDICATE)
// after
() -> placer.getIterator().increment(EXISTING_PREDICATE.or(outsideSlice()))
```

If a call site's predicate type isn't `Predicate<BlueprintPositionInfo>` (some sites use `BlueprintPositionInfo -> boolean` lambdas), inline the same slice test in that site's shape. Every stage's iterator must get the filter — a missed site = overlapping builders in that stage.

- [ ] **Step 3: Stage barrier at DECORATE**

Find the stage-advance point (symbol map: where `BuildingStructureHandler.Stage` moves to `DECORATE` — a `switch` or `nextStage()` progression in the AI/handler). Insert before the transition:

```java
if (nextStage == BuildingStructureHandler.Stage.DECORATE && getWorkOrder() != null && getWorkOrder().getSliceCount() > 1)
{
    getWorkOrder().setSolidStageDone(true);
    if (!building.getColony().getWorkManager().canEnterDecorate(getWorkOrder()))
    {
        setDelay(100); // ~5s; re-check on next AI tick
        return getState(); // stay in current state, do not advance stage
    }
}
```

Adapt `setDelay`/`getState`/colony access to the AI base-class idioms visible in the same file (the file already uses these patterns for other waits).

- [ ] **Step 4: Compile check**

Run: `.\gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A src/main/java
git commit -m "feat: builder AI slice filter and DECORATE stage barrier"
```

---

### Task 8: Network — builder count in build requests

**Files:**
- Modify: build request message class (per symbol map, expected `BuildRequestMessage.java`)
- Modify: decoration build message class (per symbol map)

**Interfaces:**
- Consumes: `WorkManager.addWorkOrderSplit` (Task 6).
- Produces: both messages carry `int builderCount` (written/read last in the buffer); their server handlers route work-order creation through `addWorkOrderSplit(order, builderCount)`.

- [ ] **Step 1: Extend the building message**

In `BuildRequestMessage`: add field `private int builderCount = 1;`, add it to the constructor used by the GUI (new parameter, default 1 in any other constructors), append `buf.writeInt(builderCount);` at the END of `toBytes`/write and `builderCount = buf.readInt();` at the END of `fromBytes`/read (same position both sides).

In its server handler (`onExecute`/`handle` per symbol map): find where the work order is created and added (currently `workManager.addWorkOrder(wo, ...)` or via building method) and route through:

```java
colony.getWorkManager().addWorkOrderSplit(wo, builderCount);
```

Keep the existing builder-assignment parameter behavior (specific builder chosen in GUI): the chosen builder claims the first child via the stock claim preference — verify how the stock message forces assignment (symbol map) and apply that forcing to child index 0 only.

- [ ] **Step 2: Same for the decoration message**

Identical pattern: field, constructor param, buffer write/read at end, handler routes through `addWorkOrderSplit`.

- [ ] **Step 3: Compile check**

Run: `.\gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A src/main/java
git commit -m "feat: builder count in build/decoration request messages"
```

---

### Task 9: GUI — builder count selector

**Files:**
- Modify: `src/main/resources/assets/minecolonies/gui/windowbuildbuilding.xml` (real name per symbol map)
- Modify: `WindowBuildBuilding.java`
- Modify: decoration controller window class + xml (per symbol map)
- Modify: lang file `src/main/resources/assets/minecolonies/lang/en_us.json`

**Interfaces:**
- Consumes: message constructors with `builderCount` (Task 8).
- Produces: `[-] N [+]` selector, range 1..`min(cap, eligible)` client-side (server still clamps), default 1; chosen N passed into the request message.

- [ ] **Step 1: Layout row**

In the build window XML, next to the existing builder-selection row, following the file's existing element style (copy attribute patterns from adjacent buttons/labels — sizes/positions must fit the window; adjust `y`-offsets of subsequent rows if the layout is absolute):

```xml
<text id="builderCountLabel" size="90 11" textalign="MIDDLE_LEFT" text="Builders:" />
<button id="builderCountDown" size="11 11" label="-" />
<text id="builderCountValue" size="30 11" textalign="MIDDLE" text="1" />
<button id="builderCountUp" size="11 11" label="+" />
```

Use a translatable key `com.minecolonies.core.gui.workerhuts.buildercount` for the label if neighboring labels use lang keys (they do — mirror them), and add to `en_us.json`:

```json
"com.minecolonies.core.gui.workerhuts.buildercount": "Builders:"
```

- [ ] **Step 2: Window logic**

In `WindowBuildBuilding` (mirror existing button registration style in the same class):

```java
private int builderCount = 1;

// in constructor/onOpened, next to existing registerButton calls:
registerButton("builderCountDown", () -> adjustBuilderCount(-1));
registerButton("builderCountUp", () -> adjustBuilderCount(1));

private void adjustBuilderCount(final int delta)
{
    final int cap = /* server cap synced client-side if available, else 16 */ 16;
    builderCount = Math.max(1, Math.min(cap, builderCount + delta));
    findPaneOfTypeByID("builderCountValue", Text.class).setText(Component.literal(String.valueOf(builderCount)));
}
```

(Client-side cap: if the server config value is not synced to clients — check symbol map for a synced-config mechanism — clamp only to 16 client-side; the server clamp from Task 6 is authoritative.)

In the confirm/build button handler where the message is sent, pass `builderCount` into the new constructor parameter.

- [ ] **Step 3: Decoration window**

Same row + same logic in the decoration controller window and its xml, passing into the decoration message.

- [ ] **Step 4: Compile + client boot check**

Run: `.\gradlew compileJava`
Expected: BUILD SUCCESSFUL.
Then: `.\gradlew runClient`, create a flat world, place a builder's hut block, open its build window: selector row visible, `-`/`+` clamp at 1 and 16, no layout overlap. Close client.

- [ ] **Step 5: Commit**

```bash
git add -A src/main
git commit -m "feat: builder count selector in build and decoration windows"
```

---

### Task 10: Integration test in dev instance

**Files:** none (verification task; fixes discovered here get their own commits)

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Launch dev client with cheats**

`.\gradlew runClient` → new creative world. Build a quick test colony:
- Place town hall (creative instant-build: `/minecolonies colony create` flow or supply-camp-free creative placement; the `mcdev` shortcut if present per repo docs — check `docs/` in fork).
- Recruit/spawn citizens, place **3 builder huts**, assign 3 builders, level huts as needed (use `/minecolonies colony setAbandoned`-style admin commands or creative build-tool instant build — whatever the fork's dev docs recommend).

- [ ] **Step 2: Core scenario — N=3 split**

Order a large building (town hall level 1→2 or a warehouse) with the selector at 3. Verify:
- Exactly 3 child work orders + 1 parent appear (`/minecolonies colony workOrders <id>` or town hall UI).
- 3 builders each claim one child and work **different X (or Z) ranges** — watch them; no builder places a block in another's slice.
- Each builder issues its own material requests.

- [ ] **Step 3: Barrier scenario**

Give builder A materials freely, starve builder B (no decorate materials until late). Verify A idles before decorating while B is still on solid stage, then both decorate after B catches up. Torches/ladders at slice boundaries survive.

- [ ] **Step 4: Completion + failure scenarios**

- Completion: when last child finishes → building levels up exactly once; parent + children gone from work-order list.
- Save/reload mid-build: `Esc → Save & Quit`, reopen world → builders resume their own slices (progress kept).
- Builder removal mid-build: fire one builder (or break hut) → its child unclaims → re-hire → child re-claimed, slice resumes.
- Cancel: cancel the build in town hall → parent and all children removed, builders stop.
- N=1 regression: order another build with selector at 1 → identical stock behavior, no parent/child records.

- [ ] **Step 5: Fix-and-commit loop**

Each bug found: fix, `gradlew compileJava`, re-verify the failing scenario, commit with `fix:` prefix. Task done when all Step 2–4 checks pass in one session.

---

### Task 11: Package and deploy to modpack

**Files:**
- Modify: `c:\Minecraft-dev-workspace\Modpacks` packwiz index (locate the minecolonies entry: `Grep -r "minecolonies" pack/ pack-two/ --include="*.toml"`)
- Modify: `Modpacks/server/run/mods/` (jar swap)

**Interfaces:**
- Consumes: final jar from `minecolonies-fork/build/libs/`.

- [ ] **Step 1: Version-stamp and build the release jar**

In fork `gradle.properties` (or wherever version is defined per Task 1 findings), suffix the version: `1.1.1255-multibuilder1`. Then `.\gradlew assemble`. Confirm `build/libs/minecolonies-1.20.1-1.1.1255-multibuilder1.jar` (name pattern per build script).

- [ ] **Step 2: Stage on a world copy**

Copy the live world (`Modpacks/server/run/world` → scratch dir), point a scratch server run at it OR use the existing backup tooling (`world-backup-*` pattern exists — follow `Modpacks/docs` conventions). Swap jar in, boot, verify: colony loads, existing work orders intact, order a small N=2 build, works.

- [ ] **Step 3: Deploy**

- Replace `minecolonies-1.20.1-1.1.1255-snapshot.jar` with the new jar in `Modpacks/server/run/mods/`.
- Update the packwiz mod entry so clients receive the new jar on next update run (packwiz local-file entry or hosted URL — follow the pack's existing pattern for any other locally-patched mod; `tools/packwiz.exe` is the binary).
- `git add` + commit the modpack changes in the Modpacks repo:

```bash
cd c:\Minecraft-dev-workspace\Modpacks
git add -A
git commit -m "feat: minecolonies multibuilder patched jar (1.1.1255-multibuilder1)"
```

- [ ] **Step 4: Live verification**

Start server (`start.ps1`), have one player update client and join, order an N=2 build on a real colony building, confirm split + no errors in `logs/latest.log`. Remind players: **everyone must update before joining** (mod version mismatch will refuse connection or desync).

---

## Self-Review Notes

- Spec coverage: UI selector (T9), network (T8), clamps + split (T3/T6), slicing model (T3/T5/T6), AI filter (T7), barrier (T6/T7), completion/cancel/failure (T6, verified T10), config (T4), deployment + packwiz + downgrade warning (T11), testing (T3 unit, T10 integration, T11 staging). No gaps found.
- Placeholder scan: no TBDs; upstream-anchored steps carry explicit STOP-and-verify instructions tied to Task 2's symbol map instead of invented line numbers — deliberate, since upstream file contents can't be read until Task 1 clones.
- Type consistency: `computeSliceCount/sliceStart/sliceEnd` (T3) match T6 usage; `setSlice/setParentId/addChildId/isMultiBuildParent/isSolidStageDone` (T5) match T6/T7 usage; `addWorkOrderSplit(order, builderCount)` (T6) matches T8 handlers; message constructor param (T8) matches T9 call.
