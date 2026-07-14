# Live Stat Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An OP-only `/stat` command that sets any of the six Discord-leaderboard numbers for any player — online or offline — while the server is running.

**Architecture:** The command lives in the existing `shababparty` Forge mod (1.2.0 → 1.3.0). It does not write `world/stats/<uuid>.json` directly — that fails, because the server flushes its in-memory `ServerStatsCounter` over the file. Instead it edits the counter the server itself owns, reached through `PlayerList`'s private `uuid → ServerStatsCounter` map via an accessor mixin, then calls `save()`. The Discord bot keeps reading the same JSON and needs no change.

**Tech Stack:** Java 17, Minecraft 1.20.1, Forge 47.4.18, SpongePowered Mixin 0.8.5, Brigadier. Built by `tools/shababparty/build.sh` (plain `javac`, no Gradle).

**Spec:** `docs/superpowers/specs/2026-07-14-live-stat-editing-design.md`
**Branch:** `feature/live-stat-editing` (already created, spec already committed)

## Global Constraints

- **Every `net.minecraft.*` symbol MUST be written in SRG form** (`m_12818_`, `f_11202_`). `build.sh` compiles against production-mapped jars and there is no reobfuscation step, so readable names like `save()` will not compile. **Forge classes (`net.minecraftforge.*`), Brigadier (`com.mojang.brigadier.*`) and authlib (`com.mojang.authlib.*`) keep their readable names** — do not SRG those.
- Every SRG name needed by this plan is already resolved in the table below. It was produced with `javap` against `server/run/libraries/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar`. **Do not guess a name that is not in this table** — run `javap` and add it.
- Java 17 (`--release 17`). Mixin `compatibilityLevel: JAVA_17`.
- Follow house style from `mixin/TamableAnimalMixin.java`: every SRG name gets a comment saying what it really is.
- No unit-test harness exists in this repo (no Gradle, no JUnit) and this plan does not add one. Verification is: `build.sh` must compile, then the real server must behave. Each task's verification steps are runnable commands with expected output.
- The server is currently **down**. It must be booted against the *local* pack for testing (`$env:PACK_URL`, see `docs/mod-workflow.md`), never the live one.

### SRG name table (verified, do not guess)

| Readable | SRG | Owner |
|---|---|---|
| `PlayerList.stats` (the `Map<UUID, ServerStatsCounter>`) | `f_11202_` | `net.minecraft.server.players.PlayerList` |
| `PlayerList.getPlayerStats(Player)` | `m_11239_` | `PlayerList` |
| `PlayerList.getPlayerByName(String)` | `m_11255_` | `PlayerList` |
| `MinecraftServer.getPlayerList()` | `m_6846_` | `net.minecraft.server.MinecraftServer` |
| `MinecraftServer.getProfileCache()` | `m_129927_` | `MinecraftServer` |
| `MinecraftServer.getWorldPath(LevelResource)` | `m_129843_` | `MinecraftServer` |
| `LevelResource.PLAYER_STATS_DIR` (`"stats"`) | `f_78175_` | `net.minecraft.world.level.storage.LevelResource` |
| `GameProfileCache.get(String)` → `Optional<GameProfile>` | `m_10996_` | `net.minecraft.server.players.GameProfileCache` |
| `ServerStatsCounter(MinecraftServer, File)` | *(constructor — not remapped)* | `net.minecraft.stats.ServerStatsCounter` |
| `ServerStatsCounter.save()` | `m_12818_` | `ServerStatsCounter` |
| `ServerStatsCounter.sendStats(ServerPlayer)` | `m_12819_` | `ServerStatsCounter` |
| `StatsCounter.setValue(Player, Stat<?>, int)` | `m_6085_` | `net.minecraft.stats.StatsCounter` |
| `StatsCounter.getValue(Stat<?>)` → `int` | `m_13015_` | `StatsCounter` |
| `StatType.get(T)` → `Stat<T>` | `m_12902_` | `net.minecraft.stats.StatType` |
| `Stats.CUSTOM` (`StatType<ResourceLocation>`) | `f_12988_` | `net.minecraft.stats.Stats` |
| `Stats.BLOCK_MINED` (`StatType<Block>`) | `f_12949_` | `Stats` |
| `Stats.ITEM_USED` (`StatType<Item>`) | `f_12982_` | `Stats` |
| `Stats.DEATHS` (`ResourceLocation`) | `f_12935_` | `Stats` |
| `Stats.MOB_KILLS` | `f_12936_` | `Stats` |
| `Stats.PLAYER_KILLS` | `f_12938_` | `Stats` |
| `Stats.PLAY_TIME` | `f_144255_` | `Stats` |
| `Entity.getUUID()` | `m_20148_` | `net.minecraft.world.entity.Entity` |
| `Commands.literal(String)` | `m_82127_` | `net.minecraft.commands.Commands` |
| `Commands.argument(String, ArgumentType)` | `m_82129_` | `Commands` |
| `CommandSourceStack.getServer()` | `m_81377_` | `net.minecraft.commands.CommandSourceStack` |
| `CommandSourceStack.hasPermission(int)` | `m_6761_` | `CommandSourceStack` |
| `CommandSourceStack.sendSuccess(Supplier<Component>, boolean)` | `m_288197_` | `CommandSourceStack` |
| `CommandSourceStack.sendFailure(Component)` | `m_81352_` | `CommandSourceStack` |
| `Component.literal(String)` | `m_237113_` | `net.minecraft.network.chat.Component` |

Two facts already verified by disassembly, relied on below:

1. `StatsCounter.m_6085_` (setValue) **never reads its `Player` argument** — its whole body is `this.f_13013_.put(stat, value)`. So the offline path may pass `null`.
2. `StatType` implements `Iterable<Stat<T>>`, so summing a whole category is a `for` loop over the StatType — no registry walk needed.

---

## File Structure

| File | Responsibility |
|---|---|
| `tools/shababparty/src/dev/alshabab/shababparty/mixin/PlayerListAccessor.java` | **new.** One job: expose `PlayerList.f_11202_`, the uuid → counter map. |
| `tools/shababparty/src/dev/alshabab/shababparty/StatsCommand.java` | **new.** The `/stat` command: argument tree, player resolution, counter access, the six fields, the sink arithmetic. |
| `tools/shababparty/res/mixins.shababparty.json` | modify — declare the accessor. |
| `tools/shababparty/res/META-INF/mods.toml` | modify — version 1.2.0 → 1.3.0. |
| `tools/shababparty/build.sh` | modify — `VERSION=1.3.0`. |
| `docs/admin-commands.md`, `docs/guides/systems.md`, `docs/CHANGELOG.md` | modify — document it. |
| `server/set-player-deaths.ps1` | **delete** — superseded. |

`StatsCommand` is one file rather than several: it is ~180 lines, has a single responsibility (the command), and splitting the six fields across files would scatter logic that changes together.

---

## Task 1: The accessor mixin

**Files:**
- Create: `tools/shababparty/src/dev/alshabab/shababparty/mixin/PlayerListAccessor.java`
- Modify: `tools/shababparty/res/mixins.shababparty.json`

**Interfaces:**
- Consumes: nothing.
- Produces: `PlayerListAccessor.shababparty$stats()` → `Map<UUID, ServerStatsCounter>`. Task 2 casts a `PlayerList` to this interface to call it.

- [ ] **Step 1: Write the mixin**

Create `tools/shababparty/src/dev/alshabab/shababparty/mixin/PlayerListAccessor.java`:

```java
package dev.alshabab.shababparty.mixin;

import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * Exposes PlayerList's private uuid -> ServerStatsCounter map.
 *
 * This map is the whole reason /stat can work on a live server. The server keeps one
 * ServerStatsCounter per player in memory and writes world/stats/<uuid>.json *from it* on autosave
 * and on logout - so editing that file by hand is pointless, the server just overwrites it. Editing
 * the counter in this map instead means the server's next flush writes our numbers back out.
 *
 * For an offline player the map has no entry. StatsCommand builds one from disk and puts it here,
 * so the server adopts it as its own and cannot later revert the edit.
 *
 * Written against SRG names because shababparty compiles straight against the production jars with
 * no reobfuscation step: f_11202_ = the stats map. (f_11197_ is players-by-uuid and f_11203_ is
 * advancements; picking the wrong one would compile and then silently corrupt the wrong thing.)
 */
@Mixin(PlayerList.class)
public interface PlayerListAccessor {
    @Accessor("f_11202_")
    Map<UUID, ServerStatsCounter> shababparty$stats();
}
```

- [ ] **Step 2: Declare it**

In `tools/shababparty/res/mixins.shababparty.json`, add `"PlayerListAccessor"` to the `mixins` array (the common one, **not** `client` — `PlayerList` must be mixed in on the dedicated server):

```json
  "mixins": [
    "TamableAnimalMixin",
    "Ability4WipMixin",
    "PlayerListAccessor"
  ],
```

- [ ] **Step 3: Compile**

Run from `Modpacks/`:

```bash
bash tools/shababparty/build.sh
```

Expected: ends with `built and installed: shababparty-1.2.0.jar` and no `javac` errors. (The version bump happens in Task 5; a 1.2.0 jar here is expected and fine.)

If `javac` reports `cannot find symbol: class Accessor`, the mixin jar is missing from the classpath — check `MIXIN_JAR` in `build.sh` resolves.

- [ ] **Step 4: Commit**

```bash
git add tools/shababparty/src/dev/alshabab/shababparty/mixin/PlayerListAccessor.java tools/shababparty/res/mixins.shababparty.json
git commit -m "shababparty: expose PlayerList's uuid -> ServerStatsCounter map"
```

---

## Task 2: The command — resolution and `/stat get`

Read-only first. This proves player resolution (online *and* offline), counter access, and the six field mappings before anything writes.

**Files:**
- Create: `tools/shababparty/src/dev/alshabab/shababparty/StatsCommand.java`

**Interfaces:**
- Consumes: `PlayerListAccessor.shababparty$stats()` from Task 1.
- Produces, for Tasks 3 and 4 (same file — they add methods to this class):
  - `private static UUID resolve(MinecraftServer, String name) throws CommandSyntaxException`
  - `private static ServerStatsCounter counterFor(MinecraftServer, String name) throws CommandSyntaxException`
  - `private static ServerPlayer onlineOrNull(MinecraftServer, String name)`
  - `private static int read(ServerStatsCounter, String field)`
  - `private static final List<String> FIELDS`

- [ ] **Step 1: Write the command**

Create `tools/shababparty/src/dev/alshabab/shababparty/StatsCommand.java`:

```java
package dev.alshabab.shababparty;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.alshabab.shababparty.mixin.PlayerListAccessor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /stat get|set|add <player> <field> [value] - edit the six numbers the Discord leaderboard ranks by,
 * for online and offline players, while the server is running.
 *
 * Why this exists: those numbers come from world/stats/<uuid>.json, and that file cannot be edited on
 * a live server. The server holds each player's stats in memory (ServerStatsCounter) and rewrites the
 * file from memory on autosave and on logout, so a hand edit is silently overwritten. The old fix was
 * server/set-player-deaths.ps1, which refused to run unless the server was stopped and covered exactly
 * one of the six columns.
 *
 * So we edit the counter the server itself flushes, via PlayerListAccessor, then save() it. The next
 * autosave writes our numbers back out instead of reverting them, and the Discord bot - which keeps
 * reading the same JSON - needs no change.
 *
 * blocks_mined and blocks_placed are not single fields: the JSON stores one entry per block type
 * (80 and 97 of them on a real player) and the board shows the sum. See setSum().
 *
 * SRG names throughout, because shababparty compiles against the production jars with no
 * reobfuscation step. Each one is commented where it is used.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StatsCommand {

    /** The six columns of the !leaderboard board, in the order they are documented. */
    public static final List<String> FIELDS = List.of(
            "deaths", "mob_kills", "player_kills", "play_time", "blocks_mined", "blocks_placed");

    /** Where the delta lands when you set one of the two sum fields. Ordinary blocks, so an inflated
     *  entry never looks anomalous. */
    private static final ResourceLocation MINED_SINK = new ResourceLocation("minecraft", "stone");
    private static final ResourceLocation PLACED_SINK = new ResourceLocation("minecraft", "dirt");

    private static final SimpleCommandExceptionType UNKNOWN_PLAYER =
            new SimpleCommandExceptionType(Component.m_237113_("No player by that name has ever joined this server."));
    private static final SimpleCommandExceptionType UNKNOWN_FIELD =
            new SimpleCommandExceptionType(Component.m_237113_("Unknown field. Valid: " + String.join(", ", FIELDS)));

    private StatsCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        // m_82127_ = literal, m_82129_ = argument, m_6761_ = hasPermission.
        // Level 3 is the op level /slr already needs, so anyone who can run that can run this.
        event.getDispatcher().register(
                Commands.m_82127_("stat")
                        .requires(src -> src.m_6761_(3))
                        .then(Commands.m_82127_("get")
                                .then(Commands.m_82129_("player", StringArgumentType.word())
                                        .then(Commands.m_82129_("field", StringArgumentType.word())
                                                .suggests(StatsCommand::suggestFields)
                                                .executes(StatsCommand::get)))));
    }

    // Pure Brigadier - no Minecraft symbol, so nothing to remap.
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestFields(
            final CommandContext<CommandSourceStack> ctx,
            final com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        final String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (final String f : FIELDS) {
            if (f.startsWith(prefix)) {
                builder.suggest(f);
            }
        }
        return builder.buildFuture();
    }

    private static int get(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack src = ctx.getSource();
        final MinecraftServer server = src.m_81377_();          // getServer
        final String name = StringArgumentType.getString(ctx, "player");
        final String field = field(ctx);

        final ServerStatsCounter counter = counterFor(server, name);
        final int value = read(counter, field);

        // m_288197_ = sendSuccess, m_237113_ = Component.literal. false = do not echo to other ops.
        src.m_288197_(() -> Component.m_237113_(name + " " + field + " = " + value + suffix(field, value)), false);
        return value;
    }

    /** play_time is stored in ticks, which is unreadable. Echo the hours so a wrong order of
     *  magnitude is obvious at a glance. */
    private static String suffix(final String field, final int value) {
        return field.equals("play_time") ? String.format(" (%.1f hours)", value / 72000.0D) : "";
    }

    private static String field(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final String field = StringArgumentType.getString(ctx, "field").toLowerCase(java.util.Locale.ROOT);
        if (!FIELDS.contains(field)) {
            throw UNKNOWN_FIELD.create();
        }
        return field;
    }

    // ---- player resolution -------------------------------------------------------------------

    /** The live player, or null if they are not online. m_6846_ = getPlayerList, m_11255_ = getPlayerByName. */
    static ServerPlayer onlineOrNull(final MinecraftServer server, final String name) {
        return server.m_6846_().m_11255_(name);
    }

    /** Online players resolve from the live entity; offline ones from the profile cache, which is
     *  usercache.json - the same file set-player-deaths.ps1 used to read. */
    static UUID resolve(final MinecraftServer server, final String name) throws CommandSyntaxException {
        final ServerPlayer online = onlineOrNull(server, name);
        if (online != null) {
            return online.m_20148_();                            // getUUID
        }
        // m_129927_ = getProfileCache, m_10996_ = get(String). GameProfile is authlib: readable.
        final Optional<GameProfile> profile = server.m_129927_().m_10996_(name);
        if (profile.isEmpty()) {
            throw UNKNOWN_PLAYER.create();
        }
        return profile.get().getId();
    }

    /**
     * The ServerStatsCounter the server itself will flush for this player.
     *
     * Online: ask PlayerList for it (m_11239_ = getPlayerStats), which is the canonical getter and
     * always returns the live in-memory counter.
     *
     * Offline: there is no entry in PlayerList's map, so build one from the player's JSON and PUT IT
     * IN THE MAP. That last part is the point - the server now owns this counter, so when it next
     * saves it writes our edit rather than reverting it.
     */
    static ServerStatsCounter counterFor(final MinecraftServer server, final String name) throws CommandSyntaxException {
        final ServerPlayer online = onlineOrNull(server, name);
        if (online != null) {
            return server.m_6846_().m_11239_(online);
        }

        final UUID uuid = resolve(server, name);
        final Map<UUID, ServerStatsCounter> stats = ((PlayerListAccessor) server.m_6846_()).shababparty$stats();
        final ServerStatsCounter cached = stats.get(uuid);
        if (cached != null) {
            // They logged out this session; the server still holds their counter and will flush it.
            return cached;
        }

        // m_129843_ = getWorldPath, f_78175_ = LevelResource.PLAYER_STATS_DIR ("stats").
        final File dir = server.m_129843_(LevelResource.f_78175_).toFile();
        final ServerStatsCounter loaded = new ServerStatsCounter(server, new File(dir, uuid + ".json"));
        stats.put(uuid, loaded);
        return loaded;
    }

    // ---- reading -----------------------------------------------------------------------------

    /** m_13015_ = StatsCounter.getValue(Stat). f_12988_ = Stats.CUSTOM, m_12902_ = StatType.get. */
    private static int custom(final ServerStatsCounter counter, final ResourceLocation id) {
        return counter.m_13015_(Stats.f_12988_.m_12902_(id));
    }

    /**
     * StatType implements Iterable<Stat<T>>, so this walks every block (or item) the game knows,
     * modded ones included - which is exactly what the Discord bot does when it sums every key under
     * "minecraft:mined" whatever its namespace. Absent entries read 0, so the sum matches the file.
     */
    private static int sumMined(final ServerStatsCounter counter) {
        int total = 0;
        for (final Stat<Block> stat : Stats.f_12949_) {          // Stats.BLOCK_MINED
            total += counter.m_13015_(stat);
        }
        return total;
    }

    private static int sumPlaced(final ServerStatsCounter counter) {
        int total = 0;
        for (final Stat<Item> stat : Stats.f_12982_) {           // Stats.ITEM_USED
            total += counter.m_13015_(stat);
        }
        return total;
    }

    static int read(final ServerStatsCounter counter, final String field) {
        switch (field) {
            case "deaths":        return custom(counter, Stats.f_12935_);
            case "mob_kills":     return custom(counter, Stats.f_12936_);
            case "player_kills":  return custom(counter, Stats.f_12938_);
            case "play_time":     return custom(counter, Stats.f_144255_);
            case "blocks_mined":  return sumMined(counter);
            case "blocks_placed": return sumPlaced(counter);
            default: throw new IllegalArgumentException(field); // field() already rejected these
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
bash tools/shababparty/build.sh
```

Expected: `built and installed: shababparty-1.2.0.jar`, no errors.

A `cannot find symbol` on any `m_`/`f_` name means it was mistyped — check it against the SRG table in Global Constraints. Do not invent a replacement.

- [ ] **Step 3: Boot the server against the local pack**

Two terminals, per `docs/mod-workflow.md`:

```powershell
Set-Alias packwiz 'C:\Minecraft-dev-workspace\tools\packwiz.exe'
cd C:\Minecraft-dev-workspace\Modpacks\pack-two
packwiz refresh
packwiz serve --port 8099
```

```powershell
cd C:\Minecraft-dev-workspace\Modpacks\server
$env:PACK_URL = "http://localhost:8099/pack.toml"
.\start.ps1
```

Expected: `Done (NN.NNNs)! For help, type "help"`.

If the log says `Mixin apply failed ... PlayerListAccessor`, the `@Accessor` name is wrong — re-check `f_11202_`.

- [ ] **Step 4: Verify reads, online and offline**

In the **server console** (no leading slash), against a player who exists in `usercache.json` but is **not** online:

```
stat get Abdulrhman-S deaths
stat get Abdulrhman-S blocks_mined
stat get Abdulrhman-S play_time
```

Expected: three lines like `Abdulrhman-S deaths = 5`, and `play_time` also prints `(30.8 hours)`.

Cross-check the numbers against the file — they must match exactly:

```bash
python -c "
import json
p=r'C:/Minecraft-dev-workspace/Modpacks/server/run/world/stats/<uuid>.json'
s=json.load(open(p))['stats']
print('deaths      ', s['minecraft:custom'].get('minecraft:deaths',0))
print('blocks_mined', sum(s.get('minecraft:mined',{}).values()))
print('play_time   ', s['minecraft:custom'].get('minecraft:play_time',0))
"
```

Then run `stat get <a-name-nobody-has>` and expect `No player by that name has ever joined this server.`

- [ ] **Step 5: Commit**

```bash
git add tools/shababparty/src/dev/alshabab/shababparty/StatsCommand.java
git commit -m "shababparty: /stat get - read any of the six leaderboard fields"
```

---

## Task 3: `/stat set` and `/stat add` for the four scalar fields

`deaths`, `mob_kills`, `player_kills`, `play_time`. The two sum fields are Task 4 and must error until then, not silently do the wrong thing.

**Files:**
- Modify: `tools/shababparty/src/dev/alshabab/shababparty/StatsCommand.java`

**Interfaces:**
- Consumes: `counterFor`, `read`, `FIELDS`, `field()` from Task 2.
- Produces, for Task 4:
  - `private static void write(MinecraftServer, String name, ServerStatsCounter, String field, int target) throws CommandSyntaxException`
  - `private static void flush(MinecraftServer, String name, ServerStatsCounter)`

- [ ] **Step 1: Register the two new subcommands**

In `onRegisterCommands`, replace the single `.then(Commands.m_82127_("get") ...)` block with all three verbs:

```java
        event.getDispatcher().register(
                Commands.m_82127_("stat")
                        .requires(src -> src.m_6761_(3))
                        .then(Commands.m_82127_("get")
                                .then(Commands.m_82129_("player", StringArgumentType.word())
                                        .then(Commands.m_82129_("field", StringArgumentType.word())
                                                .suggests(StatsCommand::suggestFields)
                                                .executes(StatsCommand::get))))
                        .then(Commands.m_82127_("set")
                                .then(Commands.m_82129_("player", StringArgumentType.word())
                                        .then(Commands.m_82129_("field", StringArgumentType.word())
                                                .suggests(StatsCommand::suggestFields)
                                                // set refuses a negative outright: no stat can be < 0.
                                                .then(Commands.m_82129_("value", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> apply(ctx, false))))))
                        .then(Commands.m_82127_("add")
                                .then(Commands.m_82129_("player", StringArgumentType.word())
                                        .then(Commands.m_82129_("field", StringArgumentType.word())
                                                .suggests(StatsCommand::suggestFields)
                                                // add takes any int - a negative amount is how you subtract.
                                                .then(Commands.m_82129_("amount", IntegerArgumentType.integer())
                                                        .executes(ctx -> apply(ctx, true)))))));
```

Add the import:

```java
import com.mojang.brigadier.arguments.IntegerArgumentType;
```

- [ ] **Step 2: Add the write path**

Append these members to `StatsCommand` (before the closing brace). `setSum` is a stub that throws until Task 4 — an honest failure beats a wrong number:

```java
    private static final SimpleCommandExceptionType NEGATIVE =
            new SimpleCommandExceptionType(Component.m_237113_("That would take the value below 0."));

    /** Shared by set and add. `relative` is true for add, where the argument is a delta. */
    private static int apply(final CommandContext<CommandSourceStack> ctx, final boolean relative)
            throws CommandSyntaxException {
        final CommandSourceStack src = ctx.getSource();
        final MinecraftServer server = src.m_81377_();
        final String name = StringArgumentType.getString(ctx, "player");
        final String field = field(ctx);

        final ServerStatsCounter counter = counterFor(server, name);
        final int before = read(counter, field);
        final int argument = IntegerArgumentType.getInteger(ctx, relative ? "amount" : "value");
        final int target = relative ? before + argument : argument;

        if (target < 0) {
            throw NEGATIVE.create();
        }

        write(server, name, counter, field, target);
        flush(server, name, counter);

        final int after = read(counter, field);
        src.m_288197_(() -> Component.m_237113_(
                name + " " + field + ": " + before + " -> " + after + suffix(field, after)), true);
        return after;
    }

    /** Sets `field` to exactly `target`. */
    private static void write(final MinecraftServer server, final String name, final ServerStatsCounter counter,
                              final String field, final int target) throws CommandSyntaxException {
        switch (field) {
            case "deaths":        setCustom(server, name, counter, Stats.f_12935_, target);  return;
            case "mob_kills":     setCustom(server, name, counter, Stats.f_12936_, target);  return;
            case "player_kills":  setCustom(server, name, counter, Stats.f_12938_, target);  return;
            case "play_time":     setCustom(server, name, counter, Stats.f_144255_, target); return;
            case "blocks_mined":  setSum(server, name, counter, field, target);              return;
            case "blocks_placed": setSum(server, name, counter, field, target);              return;
            default: throw new IllegalArgumentException(field);
        }
    }

    /**
     * m_6085_ = StatsCounter.setValue(Player, Stat, int). The Player argument is never read - the
     * whole method body is `this.stats.put(stat, value)` - which is why an offline player (no Player
     * object to pass) can safely pass null here.
     */
    private static void setCustom(final MinecraftServer server, final String name,
                                  final ServerStatsCounter counter, final ResourceLocation id, final int value) {
        counter.m_6085_(onlineOrNull(server, name), Stats.f_12988_.m_12902_(id), value);
    }

    private static void setSum(final MinecraftServer server, final String name, final ServerStatsCounter counter,
                               final String field, final int target) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(
                Component.m_237113_(field + " is not settable yet.")).create();
    }

    /**
     * m_12818_ = save(): writes world/stats/<uuid>.json right now, so the Discord bot sees the new
     * number on its next refresh rather than after the player next logs out.
     * m_12819_ = sendStats(ServerPlayer): pushes it to a live player's own stats screen so the game
     * does not show them a stale number.
     */
    private static void flush(final MinecraftServer server, final String name, final ServerStatsCounter counter) {
        counter.m_12818_();
        final ServerPlayer online = onlineOrNull(server, name);
        if (online != null) {
            counter.m_12819_(online);
        }
    }
```

- [ ] **Step 3: Compile and boot**

```bash
bash tools/shababparty/build.sh
```

Then restart the server as in Task 2 Step 3. Expected: clean `Done (NN.NNNs)!`.

- [ ] **Step 4: Verify the write survives — the whole point of the feature**

In the server console, with the target player **online**:

```
stat set <player> deaths 5
```

Expected: `<player> deaths: 0 -> 5`.

Now, **without stopping the server**, read the file. It must already say 5 — this is precisely what `set-player-deaths.ps1` could not do:

```bash
python -c "
import json
p=r'C:/Minecraft-dev-workspace/Modpacks/server/run/world/stats/<uuid>.json'
print(json.load(open(p))['stats']['minecraft:custom'].get('minecraft:deaths'))
"
```

Expected output: `5`

Then force the server to flush its own copy over ours and prove it does not revert:

```
save-all
```

Re-run the python. Expected: still `5`.

- [ ] **Step 5: Verify the offline path**

Pick a player who is **not** logged in. In the console:

```
stat set <offline-player> mob_kills 100
stat get <offline-player> mob_kills
```

Expected: `<offline-player> mob_kills: 3282 -> 100`, then `= 100`. Confirm their JSON on disk says `100`.

Then have them log in and log back out, and re-read the file. Expected: still `100` — the server adopted our counter rather than reloading the old one.

- [ ] **Step 6: Verify the guards**

```
stat set <player> deaths -1
stat add <player> deaths -9999
stat set <player> blocks_mined 5000
```

Expected, in order: Brigadier rejects `-1` at parse time (integer must not be less than 0); `That would take the value below 0.`; `blocks_mined is not settable yet.`

- [ ] **Step 7: Commit**

```bash
git add tools/shababparty/src/dev/alshabab/shababparty/StatsCommand.java
git commit -m "shababparty: /stat set and /stat add for the four scalar fields"
```

---

## Task 4: The two sum fields

**Files:**
- Modify: `tools/shababparty/src/dev/alshabab/shababparty/StatsCommand.java`

**Interfaces:**
- Consumes: `setSum` stub, `sumMined`, `sumPlaced`, `MINED_SINK`, `PLACED_SINK` from Tasks 2–3.
- Produces: nothing new — this is the last code task.

- [ ] **Step 1: Replace the `setSum` stub**

`blocks_mined` is the sum of ~80 per-block entries, so there is no single field to write. To make the sum land on `target`, move the difference into one designated block entry. The sink cannot go negative, so the lowest reachable total is `sum - sink`; below that you would have to zero other block entries and destroy per-block detail, so refuse and name the floor instead.

Replace the whole `setSum` stub with:

```java
    private static void setSum(final MinecraftServer server, final String name, final ServerStatsCounter counter,
                               final String field, final int target) throws CommandSyntaxException {
        final boolean mined = field.equals("blocks_mined");

        // f_12949_ = Stats.BLOCK_MINED, f_12982_ = Stats.ITEM_USED, m_12902_ = StatType.get.
        // ForgeRegistries is Forge's own class, so it keeps its readable name.
        final Stat<?> sink = mined
                ? Stats.f_12949_.m_12902_(ForgeRegistries.BLOCKS.getValue(MINED_SINK))
                : Stats.f_12982_.m_12902_(ForgeRegistries.ITEMS.getValue(PLACED_SINK));

        final int sum = mined ? sumMined(counter) : sumPlaced(counter);
        final int sinkValue = counter.m_13015_(sink);            // getValue
        final int floor = sum - sinkValue;

        if (target < floor) {
            final ResourceLocation id = mined ? MINED_SINK : PLACED_SINK;
            throw new SimpleCommandExceptionType(Component.m_237113_(String.format(
                    "%s cannot go below %,d without wiping other block entries (%s holds only %,d of the %,d total).",
                    field, floor, id, sinkValue, sum))).create();
        }

        // newSink = sinkValue + (target - sum) >= 0 exactly when target >= floor, which we just checked.
        counter.m_6085_(onlineOrNull(server, name), sink, sinkValue + (target - sum));
    }
```

- [ ] **Step 2: Compile and boot**

```bash
bash tools/shababparty/build.sh
```

Restart the server as in Task 2 Step 3.

- [ ] **Step 3: Verify the sum lands exactly**

Note the player's current `blocks_mined` first (`stat get <player> blocks_mined`), then:

```
stat set <player> blocks_mined 5000
```

Expected: `<player> blocks_mined: 3060 -> 5000`.

Confirm the file's entries really sum to 5000, and that only the sink moved:

```bash
python -c "
import json
p=r'C:/Minecraft-dev-workspace/Modpacks/server/run/world/stats/<uuid>.json'
m=json.load(open(p))['stats']['minecraft:mined']
print('sum  ', sum(m.values()))
print('stone', m.get('minecraft:stone'))
"
```

Expected: `sum   5000`.

Repeat for `blocks_placed` (sinks into `minecraft:dirt`, sums `minecraft:used`).

- [ ] **Step 4: Verify the floor is refused, and nothing is written**

Ask for a total below `sum - stone`:

```
stat set <player> blocks_mined 1
```

Expected: an error naming the floor, e.g.
`blocks_mined cannot go below 2,847 without wiping other block entries (minecraft:stone holds only 2,153 of the 5,000 total).`

Re-read the file: the sum must still be **5000**, unchanged.

- [ ] **Step 5: Commit**

```bash
git add tools/shababparty/src/dev/alshabab/shababparty/StatsCommand.java
git commit -m "shababparty: /stat set for blocks_mined and blocks_placed"
```

---

## Task 5: Ship it — version, pack, docs, and the dead script

**Files:**
- Modify: `tools/shababparty/build.sh`, `tools/shababparty/res/META-INF/mods.toml`
- Modify: `docs/admin-commands.md:191-204`, `docs/guides/systems.md:136-149`, `docs/CHANGELOG.md`
- Delete: `server/set-player-deaths.ps1`
- Delete: `pack-two/mods/shababparty-1.2.0.jar` (replaced by `-1.3.0.jar`)

- [ ] **Step 1: Bump the version**

In `tools/shababparty/build.sh`: `VERSION=1.2.0` → `VERSION=1.3.0`.
In `tools/shababparty/res/META-INF/mods.toml`: `version="1.2.0"` → `version="1.3.0"`.

- [ ] **Step 2: Rebuild and re-index the pack**

```bash
bash tools/shababparty/build.sh
rm Modpacks/pack-two/mods/shababparty-1.2.0.jar
```

Then, from `pack-two/`:

```powershell
packwiz refresh
```

`build.sh` copies the new jar into `pack-two/mods/` and `server/run/mods/` itself. The old 1.2.0 jar must be deleted by hand or both ship.

Verify exactly one shababparty jar remains:

```bash
ls Modpacks/pack-two/mods/ | grep shababparty
```

Expected: `shababparty-1.3.0.jar` and nothing else.

- [ ] **Step 3: Delete the superseded script**

```bash
git rm server/set-player-deaths.ps1
```

It needs the server stopped and covers one of the six columns; `/stat set <p> deaths N` does the same job live.

- [ ] **Step 4: Rewrite the admin docs**

In `docs/admin-commands.md`, replace the `### Editing a player's death count` section (lines 191-204 — the one that says "There is no command for it ... **Stop the server first.**") with:

```markdown
### Editing a player's leaderboard numbers

`/stat` sets any of the six numbers the `!leaderboard` board ranks by, for **online or offline**
players, **while the server is running**. OP only.

```
/stat get <player> <field>
/stat set <player> <field> <value>
/stat add <player> <field> <amount>      # negative amount subtracts
```

| `<field>` | |
|---|---|
| `deaths` `mob_kills` `player_kills` | straight counters |
| `play_time` | in **ticks** (20 = 1 second); the reply also prints the hours |
| `blocks_mined` `blocks_placed` | the board shows a *sum* of every block type, so setting these moves the difference into `minecraft:stone` / `minecraft:dirt` |

```
/stat set Abdulrhman-S deaths 5
/stat add Abdulrhman-S mob_kills -100
```

The number is written to `world/stats/<uuid>.json` immediately and the board picks it up on its next
refresh (`!board` to force it). It cannot be edited by hand instead: the server keeps each player's
stats in memory and rewrites that file from memory on every autosave, so a hand edit is overwritten.
`/stat` edits the copy the server itself flushes, which is why it survives.

Lowering `blocks_mined` or `blocks_placed` far enough that the sink block cannot absorb it is
refused rather than silently wiping your per-block history; the error names the floor.
```

- [ ] **Step 5: Update the player-facing guide**

In `docs/guides/systems.md`, the Leaderboards section ends with "The numbers come from the server's own statistics file, not from chat, so nobody can fake them." Leave that line — it is still true of *players* — and add after it:

```markdown
(An admin can correct a number with `/stat` if something goes wrong — a death to a server crash,
say. That is the only way any of these move other than by playing.)
```

- [ ] **Step 6: CHANGELOG**

Add to the top section of `docs/CHANGELOG.md`:

```markdown
- **Leaderboard numbers are editable on a live server.** `/stat get|set|add <player> <field>` covers all
  six columns the board ranks by — deaths, mob kills, player kills, play time, blocks mined, blocks
  placed — for online *and* offline players, with no restart. Previously only deaths could be changed,
  only by `set-player-deaths.ps1`, and only with the server stopped: `world/stats/<uuid>.json` is
  rewritten from the server's memory on every autosave, so editing it live did nothing. `/stat` edits
  the in-memory `ServerStatsCounter` the server actually flushes, so the edit sticks. The Discord bot
  is unchanged — it reads the same file it always read. `set-player-deaths.ps1` is gone.
```

- [ ] **Step 7: Full-system verification**

Restart the server one final time against the local pack. Confirm:

1. `forge mods` (server console) lists `shababparty` at **1.3.0**.
2. `stat set <player> deaths 3` → file says 3 while the server is still up.
3. `!board` in Discord → the new number renders. **This is the acceptance test**: it proves the bot needed no change.

- [ ] **Step 8: Commit**

```bash
git add -A tools/shababparty docs pack-two/mods server
git commit -m "shababparty 1.3.0: /stat, and drop set-player-deaths.ps1"
```

---

## Notes for the implementer

- **Do not add a scoreboard mirror.** An earlier idea was to mirror these six values into dummy scoreboards (the `pk_total` pattern in the `gate_kills` datapack) and have the bot read those. It was rejected: it creates a second source of truth that drifts from the file the game maintains, and it needs a rewrite of a Discord bot that lives outside this repo.
- **Do not try `/scoreboard` on a stat-criterion objective.** `scoreboard objectives add d minecraft.custom:minecraft.deaths` then `/scoreboard players set <p> d 5` looks like it should work and does nothing: stat → scoreboard is one-way, the score is clobbered on the next stat update, and the JSON is never touched.
- The bot reads `world/stats/<uuid>.json` and ranks by lifetime totals. Nothing in this plan changes that contract.
