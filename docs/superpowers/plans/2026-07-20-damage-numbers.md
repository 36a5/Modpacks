# Floating Damage Numbers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In-world damage popups in `shababparty`, split into three independently coloured and hideable buckets, with a master toggle on NUMPAD 5 and a config screen on NUMPAD 6.

**Architecture:** Damage is computed server-side, so the server pairs `LivingHurtEvent` (raw) with `LivingDamageEvent` (final) inside one tick and sends a packet to the single player who should see that number. The client keeps a small popup list, ticks it, and draws billboarded text on `RenderLevelStageEvent`. All display settings are client-side config.

**Tech Stack:** Java 17, Minecraft 1.20.1, Forge 47.4.18, packwiz. Built by `tools/shababparty/build.sh` — plain `javac` against the production jars on disk, no Gradle.

Design spec: `docs/superpowers/specs/2026-07-20-damage-numbers-design.md`

## Global Constraints

- **Minecraft methods and fields must be written in SRG names.** `build.sh` compiles against production-mapped jars with no reobfuscation step. Every SRG name in this plan was resolved from `srg_to_official_1.20.1.tsrg` in the minecolonies-fork ForgeGradle cache and cross-checked with `javap` — do not substitute readable names or invent new ones.

  Server / common:
  - `m_19879_()` = `Entity.getId()` → `int`
  - `m_9236_()` = `Entity.level()` → `Level`
  - `m_5776_()` = `Level.isClientSide()` → `boolean`
  - `m_7639_()` = `DamageSource.getEntity()` → `Entity` (the owner — a mob, not its arrow)
  - `m_5661_(Component, boolean)` = `Player.displayClientMessage()`
  - `m_237113_(String)` = `Component.literal()` → `MutableComponent`
  - `m_130130_(int)` = `FriendlyByteBuf.writeVarInt()`
  - `m_130242_()` = `FriendlyByteBuf.readVarInt()`

  Client:
  - `m_91087_()` = `Minecraft.getInstance()`
  - `f_91073_` = `Minecraft.level`, `f_91074_` = `Minecraft.player`, `f_91062_` = `Minecraft.font`, `f_91066_` = `Minecraft.options`
  - `m_91269_()` = `Minecraft.renderBuffers()` → `RenderBuffers`
  - `m_110104_()` = `RenderBuffers.bufferSource()` → `MultiBufferSource.BufferSource`
  - `m_109911_()` = `MultiBufferSource.BufferSource.endBatch()`
  - `m_91152_(Screen)` = `Minecraft.setScreen()`
  - `m_6815_(int)` = `ClientLevel.getEntity()` → `Entity`
  - `m_146892_()` = `Entity.getEyePosition()` → `Vec3` (no-arg; the `(F)` overload is `m_20299_`)
  - `m_90859_()` = `KeyMapping.consumeClick()` → `boolean`
  - `m_90583_()` = `Camera.getPosition()` → `Vec3`
  - `m_253121_()` = `Camera.rotation()` → `Quaternionf`
  - `m_85836_()` / `m_85849_()` = `PoseStack.pushPose()` / `popPose()`
  - `m_85837_(double,double,double)` = `PoseStack.translate()` (the `(FFF)` overload is `m_252880_`)
  - `m_85841_(float,float,float)` = `PoseStack.scale()`
  - `m_252781_(Quaternionf)` = `PoseStack.mulPose()`
  - `m_85850_()` = `PoseStack.last()` → `PoseStack.Pose`
  - `m_252922_()` = `PoseStack.Pose.pose()` → `Matrix4f`
  - `m_271703_(String,float,float,int,boolean,Matrix4f,MultiBufferSource,Font.DisplayMode,int,int)` = `Font.drawInBatch()` → `int`
  - `m_92895_(String)` = `Font.width()` → `int`
  - `m_7856_()` = `Screen.init()`, `m_88315_(GuiGraphics,int,int,float)` = `Screen.render()`
  - `m_142416_(GuiEventListener)` = `Screen.addRenderableWidget()`
  - `m_7379_()` = `Screen.onClose()`, `f_96541_` = `Screen.minecraft`
  - `m_6375_(double,double,int)` = `GuiEventListener.mouseClicked()` → `boolean`
  - `m_280509_(int,int,int,int,int)` = `GuiGraphics.fill(x1,y1,x2,y2,argb)`
  - `m_280488_(Font,String,int,int,int)` = `GuiGraphics.drawString()`
  - `m_94144_(String)` / `m_94155_()` = `EditBox.setValue()` / `getValue()`
  - `m_94151_(Consumer)` = `EditBox.setResponder()`
  - `m_253074_(Component, Button.OnPress)` = `Button.builder()` → `Button.Builder`
  - `m_93840_()` = `Checkbox.selected()` → `boolean`

- **Forge classes are NOT remapped.** `getSource()`, `getAmount()`, `getEntity()`, `getStage()`, `getPoseStack()`, `getPartialTick()`, `getCamera()`, `register()`, `enqueueWork()`, `setPacketHandled()` are written plainly.

- **Enum constants keep readable names.** `Font.DisplayMode.NORMAL`, `RenderLevelStageEvent.Stage.AFTER_PARTICLES`, `TickEvent.Phase.END`. Verified with `javap`.

- **Never import `org.lwjgl.glfw.GLFW`.** LWJGL is not in `server/run/libraries` and must not be added. Use the raw keycode ints (`325` = NUMPAD 5, `326` = NUMPAD 6) with the three-arg `KeyMapping(String, int, String)` constructor, which takes a keycode directly and avoids `InputConstants` entirely.

- **No client class may be reachable from a common code path.** This mod ships to `server/run/mods/` as well as `pack-two/mods/`. A dedicated server that classloads `net.minecraft.client.*` crashes on boot. Everything under `client/` is gated by `@Mod.EventBusSubscriber(value = Dist.CLIENT)` or reached through `DistExecutor.unsafeRunWhenOn`.

- **Both damage handlers MUST be `@SubscribeEvent(priority = EventPriority.LOWEST)`.** This mod already contains three handlers that mutate damage, all at default (`NORMAL`) priority:
  - `PlayerPower.onHurt` — `LivingHurtEvent`, multiplies by `1 + Strength * strengthDamagePerPoint`. This is where the Solo Leveling level actually enters the damage number.
  - `BossScaling.onHurt` — `LivingDamageEvent`, scales boss outgoing damage.
  - `DamageRelief.onHurt` — `LivingDamageEvent`, scales incoming damage.

  Forge does not order same-priority handlers deterministically. At `NORMAL`, the damage-number reads would sometimes land before `PlayerPower` and sometimes after, and the displayed number would disagree with the damage dealt at random. `LOWEST` runs last, so both reads observe the fully-modified value — weapon base, enchantments, Epic Fight, Solo Leveling Strength, boss scaling and damage relief all included.

- **Do not recompute damage from weapon stats, enchantment levels, or Solo Leveling stats.** The number to display is the one the damage pipeline produced. Any independent recomputation would duplicate logic owned by five different mods, and would silently diverge from the damage actually dealt — which defeats the entire purpose of the feature.

- **No test framework exists in this repo and this plan does not invent one.** `build.sh` is the type-check — and in an SRG-name build it is a strong one, because a wrong obfuscated name is a compile error rather than a silent runtime failure. Every task carries in-game verification with exact steps. Build early, build often.

- **Config is `ModConfig.Type.CLIENT`**, in a new `ClientConfig` class — not added to the existing `ShababParty.Config`, which is `COMMON` and already 60+ fields long.

---

## File Structure

| File | Responsibility |
|---|---|
| `tools/shababparty/libs/minecraft-1.20.1-joined-srg.jar` | **New (vendored binary).** Vanilla client+server in SRG names. Makes client code compilable at all. |
| `tools/shababparty/build.sh` | **Modify.** Swap server jar for joined jar, add netty-buffer and joml, version 1.20.0 → 1.21.0. |
| `tools/shababparty/src/.../network/Net.java` | **New.** `SimpleChannel` registration and the send helper. |
| `tools/shababparty/src/.../network/DamageNumberPacket.java` | **New.** The wire format and its handler. |
| `tools/shababparty/src/.../DamageNumbers.java` | **New.** Server: the two hooks, the raw-value stash, bucket selection. |
| `tools/shababparty/src/.../client/ClientConfig.java` | **New.** `ForgeConfigSpec` CLIENT spec plus hex parsing. |
| `tools/shababparty/src/.../client/DamageNumberKeys.java` | **New.** The two `KeyMapping`s and their tick handler. |
| `tools/shababparty/src/.../client/ClientDamageNumbers.java` | **New.** Popup list, tick/cull, world render. |
| `tools/shababparty/src/.../client/DamageNumbersScreen.java` | **New.** The config screen. |
| `tools/shababparty/src/.../ShababParty.java` | **Modify.** Register the client config, the channel, and the config-screen extension point. |
| `tools/shababparty/res/assets/shababparty/lang/en_us.json` | **Modify.** Keybind and screen strings. |
| `docs/keybinds-shabab2.md` | **Modify.** Document NUMPAD 5 / 6. |
| `docs/CHANGELOG.md` | **Modify.** 1.21.0 entry. |

---

## Task 1: Make client code compilable

Nothing else in this plan can compile until this lands. It is its own task because a reviewer can reject the dependency-vendoring approach independently of any feature code.

**Files:**
- Create: `tools/shababparty/libs/minecraft-1.20.1-joined-srg.jar` (copied binary)
- Modify: `tools/shababparty/build.sh`

**Interfaces:**
- Consumes: nothing
- Produces: a build that can resolve `net.minecraft.client.*`, `io.netty.buffer.ByteBuf`, and `org.joml.*`

- [ ] **Step 1: Vendor the joined SRG jar**

```bash
cd /c/Minecraft-dev-workspace/Modpacks/tools/shababparty
mkdir -p libs
cp "/c/Minecraft-dev-workspace/minecolonies-fork/build/fg_cache/mcp/1.20.1-20230612.114412/joined/52a985215b1b4ac60955bf4c0590228df839003d/rename/output.jar" \
   libs/minecraft-1.20.1-joined-srg.jar
```

- [ ] **Step 2: Confirm the vendored jar actually has client classes**

```bash
unzip -l libs/minecraft-1.20.1-joined-srg.jar | grep -E "client/(Minecraft|KeyMapping)\.class|client/gui/GuiGraphics\.class"
```

Expected: three lines, one per class. If this prints nothing, the wrong FG cache stage was copied — `rename/output.jar` is the correct one, `patch/output.jar` and `merge/output.jar` are not.

- [ ] **Step 3: Point build.sh at the joined jar and add the two missing runtime libs**

In `build.sh`, replace the `MC_SRG` assignment:

```bash
# Vendored rather than referenced out of minecolonies-fork's Gradle cache: a `gradle clean` over
# there, or deleting that fork, must not break this build. This is the joined (client+server)
# vanilla jar in SRG names -- the server-only jar has no net.minecraft.client at all, which is why
# nothing client-side could be compiled before.
MC_SRG="$HERE/libs/minecraft-1.20.1-joined-srg.jar"
```

Add after the `BRIGADIER_JAR` line:

```bash
# FriendlyByteBuf extends io.netty.buffer.ByteBuf, so netty must resolve before any packet code
# can call writeFloat/readFloat.
NETTY_JAR="$LIB/io/netty/netty-buffer/4.1.82.Final/netty-buffer-4.1.82.Final.jar"
# PoseStack.mulPose takes an org.joml.Quaternionf and Font.drawInBatch takes an org.joml.Matrix4f.
JOML_JAR="$LIB/org/joml/joml/1.10.5/joml-1.10.5.jar"
```

Add both to the `DEPS` array:

```bash
DEPS=("$MC_SRG" "$FORGE_JAR" "$FMLCORE_JAR" "$JAVAFML_JAR" "$EVENTBUS_JAR" "$LOG4J_JAR" \
      "$NIGHTCONFIG_JAR" "$MIXIN_JAR" "$AUTHLIB_JAR" "$FTBTEAMS_JAR" "$FTBLIB_JAR" "$SOLO_JAR" \
      "$GECKOLIB_JAR" "$BRIGADIER_JAR" "$NETTY_JAR" "$JOML_JAR")
```

- [ ] **Step 4: Bump the version**

In `build.sh`, change `VERSION=1.20.0` to `VERSION=1.21.0`.

- [ ] **Step 5: Build and confirm the existing source still compiles**

```bash
cd /c/Minecraft-dev-workspace/Modpacks/tools/shababparty && bash build.sh
```

Expected: `built and installed: shababparty-1.21.0.jar`. No javac errors. If `missing compile dependency` fires, the netty or joml path is wrong — re-check with `find /c/Minecraft-dev-workspace/Modpacks/server/run/libraries -iname "*joml*.jar"`.

- [ ] **Step 6: Commit**

```bash
cd /c/Minecraft-dev-workspace/Modpacks
git add tools/shababparty/build.sh tools/shababparty/libs/minecraft-1.20.1-joined-srg.jar
git commit -m "build: vendor joined SRG jar so client code can compile"
```

---

## Task 2: Client config

**Files:**
- Create: `tools/shababparty/src/dev/alshabab/shababparty/client/ClientConfig.java`
- Modify: `tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java`

**Interfaces:**
- Consumes: Task 1's build
- Produces:
  - `ClientConfig.SPEC` — `ForgeConfigSpec`
  - `ClientConfig.ENABLED`, `OUTGOING_ENABLED`, `MOB_TO_YOU_ENABLED`, `PLAYER_TO_YOU_ENABLED`, `SHOW_RAW`, `SHOW_FINAL` — `ForgeConfigSpec.BooleanValue`
  - `ClientConfig.OUTGOING_COLOR`, `MOB_TO_YOU_COLOR`, `PLAYER_TO_YOU_COLOR` — `ForgeConfigSpec.ConfigValue<String>`
  - `ClientConfig.LIFETIME_TICKS`, `MAX_POPUPS` — `ForgeConfigSpec.IntValue`
  - `ClientConfig.SCALE`, `RISE_SPEED` — `ForgeConfigSpec.DoubleValue`
  - `ClientConfig.PRESETS` — `String[]`, the five preset hex values
  - `static int colorOf(int bucket)` — returns 0xRRGGBB for a `DamageNumberPacket` bucket constant
  - `static boolean bucketEnabled(int bucket)` — per-bucket visibility

- [ ] **Step 1: Write ClientConfig.java**

Create `tools/shababparty/src/dev/alshabab/shababparty/client/ClientConfig.java`:

```java
package dev.alshabab.shababparty.client;

import dev.alshabab.shababparty.ShababParty;
import dev.alshabab.shababparty.network.DamageNumberPacket;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Display settings for the floating damage numbers.
 *
 * This is Type.CLIENT, not COMMON like ShababParty.Config: these are one player's preferences about
 * what their own screen looks like. Putting them in COMMON would write them to the server's config
 * directory and force every player on the server to share one colour scheme.
 *
 * Colours are stored as bare six-digit hex with no leading '#', because '#' opens a comment in TOML
 * and would have to be quoted on every hand edit. parseColor accepts either form.
 */
public final class ClientConfig {

    /** The five swatches offered in the config screen -- Minecraft's own chat-colour palette. */
    public static final String[] PRESETS = { "FF5555", "FFFF55", "55FF55", "55FFFF", "FF55FF" };

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue OUTGOING_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> OUTGOING_COLOR;
    public static final ForgeConfigSpec.BooleanValue MOB_TO_YOU_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> MOB_TO_YOU_COLOR;
    public static final ForgeConfigSpec.BooleanValue PLAYER_TO_YOU_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> PLAYER_TO_YOU_COLOR;
    public static final ForgeConfigSpec.BooleanValue SHOW_RAW;
    public static final ForgeConfigSpec.BooleanValue SHOW_FINAL;
    public static final ForgeConfigSpec.IntValue LIFETIME_TICKS;
    public static final ForgeConfigSpec.DoubleValue SCALE;
    public static final ForgeConfigSpec.DoubleValue RISE_SPEED;
    public static final ForgeConfigSpec.IntValue MAX_POPUPS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Floating damage numbers.").push("damageNumbers");

        ENABLED = b.comment("Master switch. Toggled in game with NUMPAD 5.")
                .define("enabled", true);

        b.comment("Damage you deal to anything.").push("outgoing");
        OUTGOING_ENABLED = b.define("enabled", true);
        OUTGOING_COLOR = b.comment("Six hex digits, no '#'.").define("color", "FFFF55");
        b.pop();

        b.comment("Damage mobs deal to you.").push("mobToYou");
        MOB_TO_YOU_ENABLED = b.define("enabled", true);
        MOB_TO_YOU_COLOR = b.comment("Six hex digits, no '#'.").define("color", "FF5555");
        b.pop();

        b.comment("Damage other players deal to you.").push("playerToYou");
        PLAYER_TO_YOU_ENABLED = b.define("enabled", true);
        PLAYER_TO_YOU_COLOR = b.comment("Six hex digits, no '#'.").define("color", "FF55FF");
        b.pop();

        SHOW_RAW = b.comment("Show the weapon's roll before the target's armour reduces it.")
                .define("showRaw", true);
        SHOW_FINAL = b.comment("Show the health actually removed. With both on the format is 'raw (final)'.")
                .define("showFinal", true);
        LIFETIME_TICKS = b.comment("How long a number stays on screen, in ticks. 20 = one second.")
                .defineInRange("lifetimeTicks", 20, 5, 100);
        SCALE = b.comment("Text size multiplier.")
                .defineInRange("scale", 1.0D, 0.25D, 4.0D);
        RISE_SPEED = b.comment("Blocks per second the number drifts upward.")
                .defineInRange("riseSpeed", 0.35D, 0.0D, 1.0D);
        MAX_POPUPS = b.comment("Hard cap on simultaneous numbers. The oldest is dropped at the cap.")
                .defineInRange("maxPopups", 64, 8, 256);

        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}

    /** 0xRRGGBB for a bucket, falling back to that bucket's default if the config holds junk. */
    public static int colorOf(int bucket) {
        switch (bucket) {
            case DamageNumberPacket.MOB_TO_YOU:
                return parseColor(MOB_TO_YOU_COLOR.get(), 0xFF5555);
            case DamageNumberPacket.PLAYER_TO_YOU:
                return parseColor(PLAYER_TO_YOU_COLOR.get(), 0xFF55FF);
            default:
                return parseColor(OUTGOING_COLOR.get(), 0xFFFF55);
        }
    }

    public static boolean bucketEnabled(int bucket) {
        switch (bucket) {
            case DamageNumberPacket.MOB_TO_YOU:
                return MOB_TO_YOU_ENABLED.get();
            case DamageNumberPacket.PLAYER_TO_YOU:
                return PLAYER_TO_YOU_ENABLED.get();
            default:
                return OUTGOING_ENABLED.get();
        }
    }

    /**
     * A hand-edited config file is the one place a bad colour can enter. Warn once and fall back
     * rather than throwing: a malformed hex string must not take the renderer down mid-fight.
     */
    public static int parseColor(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            ShababParty.LOGGER.warn("damage numbers: '{}' is not a hex colour, using default", raw);
            return fallback;
        }
    }
}
```

- [ ] **Step 2: Register the spec**

In `ShababParty.java`, in the constructor, immediately after the existing `registerConfig` line:

```java
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, dev.alshabab.shababparty.client.ClientConfig.SPEC);
```

The fully-qualified name is deliberate: `ClientConfig` holds no `net.minecraft.client` types, so naming it here is safe on a dedicated server, but an `import` at the top of a common class is the kind of thing that later grows into an unsafe one.

- [ ] **Step 3: Build**

```bash
cd /c/Minecraft-dev-workspace/Modpacks/tools/shababparty && bash build.sh
```

Expected: `built and installed: shababparty-1.21.0.jar`.

This will fail with `package dev.alshabab.shababparty.network does not exist` — `ClientConfig` imports `DamageNumberPacket` for its bucket constants, which Task 3 creates. Create the packet's constants now as part of this task by completing Step 4 before rebuilding.

- [ ] **Step 4: Create the packet class with its constants**

Create `tools/shababparty/src/dev/alshabab/shababparty/network/DamageNumberPacket.java` with the fields and constants only; the encode/decode/handle bodies land in Task 3:

```java
package dev.alshabab.shababparty.network;

/**
 * One damage event, addressed to the one player who should see it.
 *
 * Bucket is decided server-side because only the server knows both attacker and victim. A
 * player-versus-player hit produces two of these -- OUTGOING to the attacker, PLAYER_TO_YOU to the
 * victim -- so each sees their own side of it.
 */
public final class DamageNumberPacket {

    public static final int OUTGOING = 0;
    public static final int MOB_TO_YOU = 1;
    public static final int PLAYER_TO_YOU = 2;

    public final int entityId;
    public final float raw;
    public final float finalAmount;
    public final int bucket;

    public DamageNumberPacket(int entityId, float raw, float finalAmount, int bucket) {
        this.entityId = entityId;
        this.raw = raw;
        this.finalAmount = finalAmount;
        this.bucket = bucket;
    }
}
```

- [ ] **Step 5: Build again**

```bash
cd /c/Minecraft-dev-workspace/Modpacks/tools/shababparty && bash build.sh
```

Expected: `built and installed: shababparty-1.21.0.jar`, no errors.

- [ ] **Step 6: Verify in game**

Launch the `pack-two` client. Quit to desktop. Then:

```bash
cat /c/Minecraft-dev-workspace/Modpacks/pack-two/config/shababparty-client.toml
```

Expected: a `[damageNumbers]` block with `enabled = true`, three sub-blocks (`outgoing`, `mobToYou`, `playerToYou`) each holding `enabled` and `color`, and `showRaw`, `showFinal`, `lifetimeTicks = 20`, `scale = 1.0`, `riseSpeed = 0.35`, `maxPopups = 64`.

- [ ] **Step 7: Commit**

```bash
cd /c/Minecraft-dev-workspace/Modpacks
git add tools/shababparty/src/dev/alshabab/shababparty/client/ClientConfig.java \
        tools/shababparty/src/dev/alshabab/shababparty/network/DamageNumberPacket.java \
        tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java
git commit -m "feat: client config for damage numbers"
```

---

## Task 3: Server events and the wire

The packet is untestable without a sender, and the sender is untestable without the packet, so they land together. The client end is a log line at this stage — proving the numbers cross the wire correctly is a separate concern from drawing them, and mixing the two makes a rendering bug indistinguishable from a bucketing bug.

**Files:**
- Create: `tools/shababparty/src/dev/alshabab/shababparty/network/Net.java`
- Modify: `tools/shababparty/src/dev/alshabab/shababparty/network/DamageNumberPacket.java`
- Create: `tools/shababparty/src/dev/alshabab/shababparty/DamageNumbers.java`
- Modify: `tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java`

**Interfaces:**
- Consumes: `DamageNumberPacket(int, float, float, int)` and its bucket constants from Task 2
- Produces:
  - `Net.register()` — call once during mod construction
  - `Net.toPlayer(ServerPlayer, DamageNumberPacket)`
  - `DamageNumberPacket.encode/decode/handle` — the `SimpleChannel` triple
  - `ClientDamageNumbers.accept(DamageNumberPacket)` — created in Task 4; a temporary logging version is written here

- [ ] **Step 1: Write Net.java**

```java
package dev.alshabab.shababparty.network;

import dev.alshabab.shababparty.ShababParty;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's only network channel.
 *
 * Both accepted-version predicates return true unconditionally, which makes the channel optional:
 * a vanilla-ish server without shababparty never sends, the client never receives, and neither
 * side refuses the connection over a missing channel.
 */
public final class Net {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ShababParty.MOD_ID, "main"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(v -> true)
            .serverAcceptedVersions(v -> true)
            .simpleChannel();

    private Net() {}

    public static void register() {
        CHANNEL.registerMessage(0, DamageNumberPacket.class,
                DamageNumberPacket::encode,
                DamageNumberPacket::decode,
                DamageNumberPacket::handle);
    }

    public static void toPlayer(ServerPlayer player, DamageNumberPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
```

- [ ] **Step 2: Add encode/decode/handle to DamageNumberPacket**

Append these imports to `DamageNumberPacket.java`:

```java
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
```

and these methods to the class body:

```java
    public static void encode(DamageNumberPacket p, FriendlyByteBuf buf) {
        buf.m_130130_(p.entityId);
        buf.writeFloat(p.raw);
        buf.writeFloat(p.finalAmount);
        buf.m_130130_(p.bucket);
    }

    public static DamageNumberPacket decode(FriendlyByteBuf buf) {
        return new DamageNumberPacket(buf.m_130242_(), buf.readFloat(), buf.readFloat(), buf.m_130242_());
    }

    /**
     * The doubly-nested supplier in unsafeRunWhenOn is not a style choice. It keeps the reference to
     * ClientDamageNumbers inside a lambda body that is only ever instantiated on the client, so a
     * dedicated server never classloads a net.minecraft.client type and never crashes on boot.
     */
    public static void handle(DamageNumberPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> dev.alshabab.shababparty.client.ClientDamageNumbers.accept(p)));
        ctx.get().setPacketHandled(true);
    }
```

- [ ] **Step 3: Write the temporary client sink**

Create `tools/shababparty/src/dev/alshabab/shababparty/client/ClientDamageNumbers.java`:

```java
package dev.alshabab.shababparty.client;

import dev.alshabab.shababparty.ShababParty;
import dev.alshabab.shababparty.network.DamageNumberPacket;

public final class ClientDamageNumbers {

    private ClientDamageNumbers() {}

    public static void accept(DamageNumberPacket p) {
        ShababParty.LOGGER.info("damage number: bucket={} entity={} raw={} final={}",
                p.bucket, p.entityId, p.raw, p.finalAmount);
    }
}
```

- [ ] **Step 4: Write DamageNumbers.java**

```java
package dev.alshabab.shababparty;

import java.util.HashMap;
import java.util.Map;

import dev.alshabab.shababparty.network.DamageNumberPacket;
import dev.alshabab.shababparty.network.Net;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Turns damage into packets.
 *
 * Neither Forge event carries both numbers the feature needs: LivingHurtEvent has the raw incoming
 * amount before armour and enchantment reduction, LivingDamageEvent has the final amount that will
 * leave the health bar. They fire in that order within one tick for the same victim, so the raw
 * value is stashed by entity id and consumed by the second event.
 *
 * <h2>Why both handlers are LOWEST priority</h2>
 * This mod already mutates damage in three places, all at default priority: PlayerPower multiplies
 * LivingHurtEvent by the Solo Leveling Strength stat, and BossScaling and DamageRelief both scale
 * LivingDamageEvent. Forge does not order same-priority handlers deterministically, so reading the
 * amount at NORMAL would sometimes see PlayerPower's multiplier and sometimes not -- the number on
 * screen would disagree with the damage dealt, at random, which is worse than showing nothing.
 *
 * LOWEST runs last. Both reads therefore observe the fully-modified value: weapon base damage,
 * enchantments, Epic Fight's combat maths, the Solo Leveling Strength multiplier, boss scaling and
 * damage relief, all already applied. Nothing here recomputes damage -- it reports what the
 * pipeline produced, which is the only number that can be trusted to match reality.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID)
public final class DamageNumbers {

    private static final Map<Integer, Float> RAW = new HashMap<>();

    private DamageNumbers() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().m_9236_().m_5776_()) {
            return;
        }
        RAW.put(event.getEntity().m_19879_(), event.getAmount());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.m_9236_().m_5776_()) {
            return;
        }

        int victimId = victim.m_19879_();
        Float stashed = RAW.remove(victimId);
        float finalAmount = event.getAmount();

        // A stream of "0" popups from an immune boss phase is noise, not information.
        if (finalAmount <= 0.0F) {
            return;
        }
        float rawAmount = stashed == null ? finalAmount : stashed;

        Entity attacker = event.getSource().m_7639_();
        boolean selfInflicted = attacker == victim;

        if (!selfInflicted && attacker instanceof ServerPlayer dealer) {
            Net.toPlayer(dealer, new DamageNumberPacket(
                    victimId, rawAmount, finalAmount, DamageNumberPacket.OUTGOING));
        }

        if (victim instanceof ServerPlayer target) {
            // Self-inflicted damage counts as generic incoming rather than PvP -- being set on fire
            // by your own Fire Aspect is not another player hitting you.
            int bucket = (!selfInflicted && attacker instanceof Player)
                    ? DamageNumberPacket.PLAYER_TO_YOU
                    : DamageNumberPacket.MOB_TO_YOU;
            Net.toPlayer(target, new DamageNumberPacket(victimId, rawAmount, finalAmount, bucket));
        }
    }

    /**
     * A LivingDamageEvent cancelled by another mod leaves its raw value stashed with nothing to
     * consume it. Hurt and damage always pair within one tick, so anything still here at end of
     * tick is orphaned and clearing it wholesale is both correct and cheap.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !RAW.isEmpty()) {
            RAW.clear();
        }
    }
}
```

- [ ] **Step 5: Register the channel**

In `ShababParty.java`, in the constructor, after the client config registration:

```java
        dev.alshabab.shababparty.network.Net.register();
```

- [ ] **Step 6: Build**

```bash
cd /c/Minecraft-dev-workspace/Modpacks/tools/shababparty && bash build.sh
```

Expected: `built and installed: shababparty-1.21.0.jar`, no errors.

- [ ] **Step 7: Verify the numbers cross the wire**

Launch the `pack-two` client and open a single-player world (or connect to the server if it is running the new jar).

1. Hit a passive mob with any weapon. Check `pack-two/logs/latest.log`:
   `damage number: bucket=0 entity=<n> raw=<x> final=<y>` — bucket 0 is OUTGOING.
2. Let a hostile mob hit you. Expect `bucket=1` (MOB_TO_YOU).
3. Hit an armoured mob (an iron golem, or a Cataclysm boss). Expect `raw` and `final` to differ visibly — this is the pairing working.
4. Stand in a fire and take burn damage. Expect `bucket=1`, not `bucket=2`.

If `raw` always equals `final`, the `LivingHurtEvent` handler is not firing — check the `@Mod.EventBusSubscriber` modid matches.

- [ ] **Step 7b: Verify the Solo Leveling Strength multiplier is included**

This is the check that `EventPriority.LOWEST` is actually working. Without it the numbers are right roughly half the time, which is very hard to notice by eye.

Use a creative-mode test world, not your live save — this spends stat points.

1. Note your current Strength in the Solo Leveling panel, and the logged `raw` for one hit on a passive mob with a fixed weapon.
2. `/invest 1000`, then click `+` on Strength in the Solo Leveling panel a few times. (There is no command that sets Strength directly; `/invest` sets how many points each `+` click spends, and the panel does the spending.)
3. Hit the same mob type with the same weapon.

Expected: `raw` has grown by roughly the factor `1 + addedStrength * strengthDamagePerPoint`, where `strengthDamagePerPoint` is read from `server/run/config/shababparty-common.toml`. If `raw` did not move at all, this handler ran before `PlayerPower` — confirm both `@SubscribeEvent` annotations in `DamageNumbers` carry `priority = EventPriority.LOWEST`.

4. Restore: `/soloresetstats`, or use the mod's own Stat Redistribution item.

- [ ] **Step 8: Verify the dedicated server still boots**

```bash
cd /c/Minecraft-dev-workspace/Modpacks/server/run && ls mods/shababparty-1.21.0.jar
```

Start the server. Expected: it reaches "Done" with no `NoClassDefFoundError: net/minecraft/client/...`. This is the check that the `DistExecutor` gating in Step 2 is correct. Stop the server.

- [ ] **Step 9: Commit**

```bash
cd /c/Minecraft-dev-workspace/Modpacks
git add tools/shababparty/src/dev/alshabab/shababparty/network/ \
        tools/shababparty/src/dev/alshabab/shababparty/DamageNumbers.java \
        tools/shababparty/src/dev/alshabab/shababparty/client/ClientDamageNumbers.java \
        tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java
git commit -m "feat: send damage events to the player who should see them"
```

---

## Task 4: Render the popups

**Files:**
- Modify: `tools/shababparty/src/dev/alshabab/shababparty/client/ClientDamageNumbers.java`

**Interfaces:**
- Consumes: `ClientConfig.*` from Task 2, `DamageNumberPacket` from Task 3
- Produces: `ClientDamageNumbers.accept(DamageNumberPacket)` (real implementation), rendering on `RenderLevelStageEvent`

- [ ] **Step 1: Replace ClientDamageNumbers.java entirely**

```java
package dev.alshabab.shababparty.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alshabab.shababparty.ShababParty;
import dev.alshabab.shababparty.network.DamageNumberPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The client's popup list, and the code that draws it.
 *
 * Popups hold a fixed world position captured at spawn rather than a live entity reference: the
 * number should stay where the hit landed even if the mob walks away or dies on the same tick, and
 * holding an Entity would keep dead entities reachable.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, value = Dist.CLIENT)
public final class ClientDamageNumbers {

    /** Full-bright. Damage numbers should not dim in a cave. */
    private static final int FULL_BRIGHT = 0x00F000F0;

    /** Base world-units-per-pixel for in-world text. The vanilla nameplate constant. */
    private static final float BASE_SCALE = 0.025F;

    private static final List<Popup> POPUPS = new ArrayList<>();

    private ClientDamageNumbers() {}

    private static final class Popup {
        final double x;
        final double y;
        final double z;
        final String text;
        final int rgb;
        final int lifetime;
        int age;

        Popup(double x, double y, double z, String text, int rgb, int lifetime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.text = text;
            this.rgb = rgb;
            this.lifetime = lifetime;
        }
    }

    public static void accept(DamageNumberPacket p) {
        if (!ClientConfig.ENABLED.get() || !ClientConfig.bucketEnabled(p.bucket)) {
            return;
        }

        Minecraft mc = Minecraft.m_91087_();
        ClientLevel level = mc.f_91073_;
        if (level == null) {
            return;
        }

        // Out of render distance, or already removed. Normal, not an error.
        Entity victim = level.m_6815_(p.entityId);
        if (victim == null) {
            return;
        }

        String text = format(p);
        if (text.isEmpty()) {
            return;
        }

        Vec3 at = victim.m_146892_();

        // Same-tick hits would otherwise render exactly on top of each other. This is not polish:
        // Cataclysm's Meat Shredder ignores invincibility frames and Epic Fight combos land 3-5
        // hits per swing, so overlapping numbers are the normal case for the weapons most worth
        // measuring. Jitter is derived from list size so it is stable frame to frame.
        double jitter = ((POPUPS.size() % 5) - 2) * 0.18D;

        POPUPS.add(new Popup(at.x + jitter, at.y, at.z,
                text, ClientConfig.colorOf(p.bucket), ClientConfig.LIFETIME_TICKS.get()));

        // Without a cap, a Meat Shredder held down in a crowd is an unbounded allocation.
        while (POPUPS.size() > ClientConfig.MAX_POPUPS.get()) {
            POPUPS.remove(0);
        }
    }

    private static String format(DamageNumberPacket p) {
        boolean raw = ClientConfig.SHOW_RAW.get();
        boolean fin = ClientConfig.SHOW_FINAL.get();
        if (raw && fin) {
            return trim(p.raw) + " (" + trim(p.finalAmount) + ")";
        }
        if (raw) {
            return trim(p.raw);
        }
        if (fin) {
            return trim(p.finalAmount);
        }
        return "";
    }

    /** One decimal place, with a trailing ".0" dropped -- "9" reads better than "9.0" mid-fight. */
    private static String trim(float value) {
        String s = String.format("%.1f", value);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || POPUPS.isEmpty()) {
            return;
        }
        POPUPS.removeIf(p -> ++p.age >= p.lifetime);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (POPUPS.isEmpty() || !ClientConfig.ENABLED.get()) {
            return;
        }

        Minecraft mc = Minecraft.m_91087_();
        Font font = mc.f_91062_;
        Camera camera = event.getCamera();
        Vec3 camPos = camera.m_90583_();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.m_91269_().m_110104_();

        float partial = event.getPartialTick();
        float scale = (float) (double) ClientConfig.SCALE.get();
        float rise = (float) (double) ClientConfig.RISE_SPEED.get();

        for (Popup p : POPUPS) {
            float age = p.age + partial;
            float life = age / p.lifetime;
            if (life >= 1.0F) {
                continue;
            }

            int alpha = (int) ((1.0F - life) * 255.0F);
            if (alpha < 8) {
                continue;
            }
            int argb = (alpha << 24) | p.rgb;

            pose.m_85836_();
            pose.m_85837_(p.x - camPos.x, p.y - camPos.y + (age / 20.0F) * rise, p.z - camPos.z);
            // Billboard: adopting the camera's rotation makes the quad face the viewer. The
            // negative scale on x and y then flips it, because in-world text is otherwise mirrored
            // and upside down.
            pose.m_252781_(camera.m_253121_());
            pose.m_85841_(-BASE_SCALE * scale, -BASE_SCALE * scale, BASE_SCALE * scale);

            Matrix4f matrix = pose.m_85850_().m_252922_();
            float half = font.m_92895_(p.text) / 2.0F;

            font.m_271703_(p.text, -half, 0.0F, argb, false, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);

            pose.m_85849_();
        }

        buffers.m_109911_();
    }

    /** Leaving the world must not carry popups into the next one. */
    @SubscribeEvent
    public static void onLoggedOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        POPUPS.clear();
    }
}
```

- [ ] **Step 2: Build**

```bash
cd /c/Minecraft-dev-workspace/Modpacks/tools/shababparty && bash build.sh
```

Expected: `built and installed: shababparty-1.21.0.jar`, no errors.

- [ ] **Step 3: Verify in game**

Launch `pack-two`.

1. Hit a passive mob. A yellow number rises from it and fades over about a second.
2. Take a hit from a hostile mob. A red number appears on you.
3. Hit an armoured mob. The number reads `raw (final)` with two different values, e.g. `24 (9)`.
4. Walk around the number while it is visible — it stays facing you and stays readable, not mirrored or upside down.
5. Hit a crowd with a fast weapon. Numbers spread horizontally instead of stacking on one spot.
6. Edit `pack-two/config/shababparty-client.toml`, set `[damageNumbers.outgoing] enabled = false`, rejoin. Yellow numbers stop; red still appear.

If the text renders mirrored, the negative scale in Step 1 was dropped. If it renders behind terrain, `Font.DisplayMode.NORMAL` was changed to `SEE_THROUGH`.

- [ ] **Step 4: Commit**

```bash
cd /c/Minecraft-dev-workspace/Modpacks
git add tools/shababparty/src/dev/alshabab/shababparty/client/ClientDamageNumbers.java
git commit -m "feat: render floating damage numbers"
```

---

## Task 5: Keybinds

**Files:**
- Create: `tools/shababparty/src/dev/alshabab/shababparty/client/DamageNumberKeys.java`
- Modify: `tools/shababparty/res/assets/shababparty/lang/en_us.json`

**Interfaces:**
- Consumes: `ClientConfig.ENABLED` from Task 2
- Produces: `DamageNumberKeys.TOGGLE`, `DamageNumberKeys.CONFIG` — `KeyMapping`

- [ ] **Step 1: Write DamageNumberKeys.java**

```java
package dev.alshabab.shababparty.client;

import dev.alshabab.shababparty.ShababParty;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Both binds are real KeyMappings rather than raw key polling, which is what makes them show up in
 * Options -> Controls -> Shabab Party and be rebindable there. Minecraft already does conflict
 * detection and persistence; none of that needs writing.
 *
 * The keycodes are raw ints because org.lwjgl is not on this build's classpath and must not be
 * added -- the three-arg KeyMapping constructor takes a keycode directly, sidestepping
 * InputConstants and GLFW entirely.
 */
public final class DamageNumberKeys {

    private static final String CATEGORY = "key.categories.shababparty";

    /** GLFW_KEY_KP_5. */
    private static final int NUMPAD_5 = 325;
    /** GLFW_KEY_KP_6. */
    private static final int NUMPAD_6 = 326;

    public static final KeyMapping TOGGLE =
            new KeyMapping("key.shababparty.toggle_damage_numbers", NUMPAD_5, CATEGORY);

    public static final KeyMapping CONFIG =
            new KeyMapping("key.shababparty.damage_numbers_config", NUMPAD_6, CATEGORY);

    private DamageNumberKeys() {}

    @Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE);
            event.register(CONFIG);
        }
    }

    @Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, value = Dist.CLIENT)
    public static final class Handler {
        private Handler() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.m_91087_();
            if (mc.f_91074_ == null) {
                return;
            }

            while (TOGGLE.m_90859_()) {
                boolean now = !ClientConfig.ENABLED.get();
                ClientConfig.ENABLED.set(now);
                ClientConfig.ENABLED.save();
                // A toggle whose only feedback is the absence of numbers in a quiet moment is
                // indistinguishable from a toggle that did nothing.
                mc.f_91074_.m_5661_(Component.m_237113_(
                        now ? "Damage numbers: on" : "Damage numbers: off"), true);
            }

            while (CONFIG.m_90859_()) {
                mc.m_91152_(new DamageNumbersScreen(null));
            }
        }
    }
}
```

This will not compile until Task 6 creates `DamageNumbersScreen`. Complete Step 2, then jump to Task 6 Step 1, then return here for Step 3.

- [ ] **Step 2: Add the lang strings**

In `tools/shababparty/res/assets/shababparty/lang/en_us.json`, add these entries (keep the existing ones):

```json
  "key.categories.shababparty": "Shabab Party",
  "key.shababparty.toggle_damage_numbers": "Toggle Damage Numbers",
  "key.shababparty.damage_numbers_config": "Damage Numbers Settings",
  "shababparty.damagenumbers.title": "Damage Numbers",
  "shababparty.damagenumbers.master": "Show damage numbers",
  "shababparty.damagenumbers.outgoing": "Damage you deal",
  "shababparty.damagenumbers.mob_to_you": "Mobs to you",
  "shababparty.damagenumbers.player_to_you": "Players to you",
  "shababparty.damagenumbers.show_raw": "Show raw damage",
  "shababparty.damagenumbers.show_final": "Show final damage",
  "shababparty.damagenumbers.keybinds": "Edit Keybinds",
  "shababparty.damagenumbers.done": "Done"
```

Verify the file is still valid JSON — a trailing comma here is a silent resource-pack failure, not a crash:

```bash
python -c "import json;json.load(open(r'C:/Minecraft-dev-workspace/Modpacks/tools/shababparty/res/assets/shababparty/lang/en_us.json'));print('ok')"
```

Expected: `ok`

- [ ] **Step 3: Build (after Task 6 Step 1 exists)**

```bash
cd /c/Minecraft-dev-workspace/Modpacks/tools/shababparty && bash build.sh
```

Expected: `built and installed: shababparty-1.21.0.jar`, no errors.

- [ ] **Step 4: Verify in game**

Launch `pack-two`.

1. Options → Controls → scroll to a **Shabab Party** category holding "Toggle Damage Numbers" (NUMPAD 5) and "Damage Numbers Settings" (NUMPAD 6). Neither shows a conflict marker.
2. In world, press NUMPAD 5. Action bar reads "Damage numbers: off". Hit a mob — no number.
3. Press NUMPAD 5 again. "Damage numbers: on". Numbers return.
4. Quit and relaunch with it off. It is still off — the setting persisted.
5. Rebind the toggle to a different key in Controls, confirm the new key works and NUMPAD 5 no longer does.

- [ ] **Step 5: Commit**

```bash
cd /c/Minecraft-dev-workspace/Modpacks
git add tools/shababparty/src/dev/alshabab/shababparty/client/DamageNumberKeys.java \
        tools/shababparty/res/assets/shababparty/lang/en_us.json
git commit -m "feat: damage number keybinds on numpad 5 and 6"
```

---

## Task 6: Config screen

**Files:**
- Create: `tools/shababparty/src/dev/alshabab/shababparty/client/DamageNumbersScreen.java`
- Modify: `tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java`

**Interfaces:**
- Consumes: `ClientConfig.*` from Task 2, `DamageNumberKeys` from Task 5
- Produces: `new DamageNumbersScreen(Screen parent)` — pass `null` when opened from a keybind

- [ ] **Step 1: Write DamageNumbersScreen.java**

```java
package dev.alshabab.shababparty.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Hand-rolled rather than Cloth Config. Cloth is in the pack and would be less code, but adding it
 * to build.sh turns a dependency-free build into one that breaks when a pack update moves Cloth's
 * version, and makes shababparty hard-require a mod it otherwise does not need.
 *
 * Each bucket row is: a checkbox, five preset swatches, a hex field, and a live preview. The
 * swatches are drawn and hit-tested by hand because a five-item colour palette does not justify a
 * widget class.
 */
public final class DamageNumbersScreen extends Screen {

    private static final int ROW_HEIGHT = 28;
    private static final int SWATCH = 12;
    private static final int SWATCH_GAP = 3;

    private final Screen parent;

    private Checkbox master;
    private Checkbox showRaw;
    private Checkbox showFinal;

    private final Row[] rows = {
            new Row("shababparty.damagenumbers.outgoing",
                    ClientConfig.OUTGOING_ENABLED, ClientConfig.OUTGOING_COLOR, 0xFFFF55),
            new Row("shababparty.damagenumbers.mob_to_you",
                    ClientConfig.MOB_TO_YOU_ENABLED, ClientConfig.MOB_TO_YOU_COLOR, 0xFF5555),
            new Row("shababparty.damagenumbers.player_to_you",
                    ClientConfig.PLAYER_TO_YOU_ENABLED, ClientConfig.PLAYER_TO_YOU_COLOR, 0xFF55FF),
    };

    private static final class Row {
        final String labelKey;
        final ForgeConfigSpec.BooleanValue enabled;
        final ForgeConfigSpec.ConfigValue<String> color;
        final int fallback;

        Checkbox checkbox;
        EditBox hex;
        int swatchX;
        int swatchY;

        Row(String labelKey, ForgeConfigSpec.BooleanValue enabled,
            ForgeConfigSpec.ConfigValue<String> color, int fallback) {
            this.labelKey = labelKey;
            this.enabled = enabled;
            this.color = color;
            this.fallback = fallback;
        }

        int rgb() {
            return ClientConfig.parseColor(hex == null ? color.get() : hex.m_94155_(), fallback);
        }
    }

    public DamageNumbersScreen(Screen parent) {
        super(Component.m_237115_("shababparty.damagenumbers.title"));
        this.parent = parent;
    }

    @Override
    protected void m_7856_() {
        int left = this.f_96543_ / 2 - 150;
        int y = 40;

        this.master = new Checkbox(left, y, 200, 20,
                Component.m_237115_("shababparty.damagenumbers.master"), ClientConfig.ENABLED.get());
        this.m_142416_(this.master);
        y += ROW_HEIGHT;

        for (Row row : rows) {
            row.checkbox = new Checkbox(left, y, 150, 20,
                    Component.m_237115_(row.labelKey), row.enabled.get());
            this.m_142416_(row.checkbox);

            row.swatchX = left + 150;
            row.swatchY = y + 4;

            row.hex = new EditBox(this.f_96547_,
                    left + 150 + (SWATCH + SWATCH_GAP) * ClientConfig.PRESETS.length + 6, y,
                    56, 20, Component.m_237113_("hex"));
            row.hex.m_94199_(7);
            row.hex.m_94144_(row.color.get());
            this.m_142416_(row.hex);

            y += ROW_HEIGHT;
        }

        y += 4;
        this.showRaw = new Checkbox(left, y, 145, 20,
                Component.m_237115_("shababparty.damagenumbers.show_raw"), ClientConfig.SHOW_RAW.get());
        this.m_142416_(this.showRaw);
        this.showFinal = new Checkbox(left + 155, y, 145, 20,
                Component.m_237115_("shababparty.damagenumbers.show_final"), ClientConfig.SHOW_FINAL.get());
        this.m_142416_(this.showFinal);
        y += ROW_HEIGHT + 6;

        this.m_142416_(Button.m_253074_(
                Component.m_237115_("shababparty.damagenumbers.keybinds"),
                b -> this.f_96541_.m_91152_(new KeyBindsScreen(this, this.f_96541_.f_91066_)))
                .m_252794_(left, y).m_253046_(145, 20).m_253136_());

        this.m_142416_(Button.m_253074_(
                Component.m_237115_("shababparty.damagenumbers.done"), b -> this.m_7379_())
                .m_252794_(left + 155, y).m_253046_(145, 20).m_253136_());
    }

    @Override
    public boolean m_6375_(double mouseX, double mouseY, int button) {
        for (Row row : rows) {
            for (int i = 0; i < ClientConfig.PRESETS.length; i++) {
                int x = row.swatchX + i * (SWATCH + SWATCH_GAP);
                if (mouseX >= x && mouseX < x + SWATCH
                        && mouseY >= row.swatchY && mouseY < row.swatchY + SWATCH) {
                    row.hex.m_94144_(ClientConfig.PRESETS[i]);
                    return true;
                }
            }
        }
        return super.m_6375_(mouseX, mouseY, button);
    }

    @Override
    public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.m_280273_(g);
        super.m_88315_(g, mouseX, mouseY, partial);

        g.m_280488_(this.f_96547_, this.f_96539_.getString(),
                this.f_96543_ / 2 - this.f_96547_.m_92895_(this.f_96539_.getString()) / 2, 18, 0xFFFFFF);

        for (Row row : rows) {
            int active = row.rgb();
            for (int i = 0; i < ClientConfig.PRESETS.length; i++) {
                int x = row.swatchX + i * (SWATCH + SWATCH_GAP);
                int rgb = ClientConfig.parseColor(ClientConfig.PRESETS[i], row.fallback);
                // The outlined swatch is the one currently in effect, so the bucket-to-colour
                // mapping is readable without parsing hex.
                if (rgb == active) {
                    g.m_280509_(x - 1, row.swatchY - 1, x + SWATCH + 1, row.swatchY + SWATCH + 1,
                            0xFFFFFFFF);
                }
                g.m_280509_(x, row.swatchY, x + SWATCH, row.swatchY + SWATCH, 0xFF000000 | rgb);
            }

            // Live preview of whatever is in the hex field right now.
            int previewX = row.hex.m_252754_() + row.hex.m_5711_() + 6;
            g.m_280509_(previewX, row.swatchY, previewX + SWATCH, row.swatchY + SWATCH,
                    0xFF000000 | active);
        }
    }

    /**
     * Everything is written on close rather than on every keystroke: a half-typed hex string is not
     * a colour, and writing it would spam the config file and log a warning per character.
     */
    @Override
    public void m_7379_() {
        ClientConfig.ENABLED.set(this.master.m_93840_());
        ClientConfig.SHOW_RAW.set(this.showRaw.m_93840_());
        ClientConfig.SHOW_FINAL.set(this.showFinal.m_93840_());

        for (Row row : rows) {
            row.enabled.set(row.checkbox.m_93840_());
            String typed = row.hex.m_94155_();
            String clean = typed.startsWith("#") ? typed.substring(1) : typed;
            // Reject junk by keeping the previous value rather than storing something the renderer
            // will have to warn about on every frame.
            if (clean.length() == 6 && clean.chars().allMatch(
                    c -> Character.digit(c, 16) >= 0)) {
                row.color.set(clean.toUpperCase());
            }
        }

        ClientConfig.SPEC.save();
        this.f_96541_.m_91152_(this.parent);
    }
}
```

- [ ] **Step 2: (Reference) the screen-only SRG names used above**

Every name in Step 1 was resolved from `srg_to_official_1.20.1.tsrg` before this plan was written. The ones that appear only in this task, for review purposes:

| SRG | Official |
|---|---|
| `f_96543_` | `Screen.width` |
| `f_96544_` | `Screen.height` |
| `f_96547_` | `Screen.font` |
| `f_96539_` | `Screen.title` |
| `m_280273_` | `Screen.renderBackground(GuiGraphics)` |
| `m_237115_` | `Component.translatable(String)` |
| `m_94199_` | `EditBox.setMaxLength(int)` |
| `m_252754_` | `AbstractWidget.getX()` |
| `m_5711_` | `AbstractWidget.getWidth()` |
| `m_252794_` | `Button.Builder.pos(int,int)` |
| `m_253046_` | `Button.Builder.size(int,int)` |
| `m_253136_` | `Button.Builder.build()` |

To re-derive any name yourself, the mapping file is at
`minecolonies-fork/build/fg_cache/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/srg_to_official_1.20.1.tsrg`.
Format is `tsrg2`: an unindented `<srg-class> <official-class>` line, then tab-indented members —
`<srg> <official>` for fields, `<srg> <descriptor> <official>` for methods. Grep the class line, then
read the block under it. Note the direction: the file is keyed by SRG name, so to go from a readable
name to an SRG one you search the *second* column.

- [ ] **Step 3: Register the Mods-list entry point**

In `ShababParty.java`, in the constructor:

```java
        // Also reachable from the Mods list, not only the keybind.
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> ModLoadingContext.get().registerExtensionPoint(
                        net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                                (mc, parent) -> new dev.alshabab.shababparty.client.DamageNumbersScreen(parent))));
```

- [ ] **Step 4: Build**

```bash
cd /c/Minecraft-dev-workspace/Modpacks/tools/shababparty && bash build.sh
```

Expected: `built and installed: shababparty-1.21.0.jar`, no errors. Task 5 also compiles now.

- [ ] **Step 5: Verify in game**

Launch `pack-two`.

1. Press NUMPAD 6 in world. The screen opens with three rows, five swatches each, hex fields pre-filled `FFFF55` / `FF5555` / `FF55FF`, and one outlined swatch per row matching the current colour.
2. Click the green swatch on "Damage you deal". The hex field becomes `55FF55`, the preview turns green, the outline moves.
3. Click Done, hit a mob — the number is green.
4. Reopen, type `00AAFF` into a hex field by hand, Done, hit a mob — the number is that blue.
5. Reopen, type `zzz` into a hex field, Done. The previous colour is kept and no warning spams the log.
6. Untick "Mobs to you", Done. Mob hits produce no number; your own hits still do.
7. Untick "Show raw", Done. Numbers show one value, not `raw (final)`.
8. Click "Edit Keybinds" — the vanilla controls screen opens. Back out and it returns to this screen.
9. Mods list → Shabab Party → Config. Same screen opens.
10. `cat pack-two/config/shababparty-client.toml` — the values match what the screen showed.

- [ ] **Step 6: Commit**

```bash
cd /c/Minecraft-dev-workspace/Modpacks
git add tools/shababparty/src/dev/alshabab/shababparty/client/DamageNumbersScreen.java \
        tools/shababparty/src/dev/alshabab/shababparty/ShababParty.java
git commit -m "feat: damage numbers config screen"
```

---

## Task 7: Ship it

**Files:**
- Modify: `docs/keybinds-shabab2.md`
- Modify: `docs/CHANGELOG.md`
- Modify: `pack-two/` packwiz index

**Interfaces:**
- Consumes: everything above
- Produces: a distributable pack update

- [ ] **Step 1: Full-stack verification**

Start the dedicated server with the new jar, connect the `pack-two` client to it, and run the spec's acceptance list:

1. Hit a mob — yellow `raw (final)`.
2. Take a mob hit — red.
3. Have a second player hit you — purple on your screen, yellow on theirs, simultaneously.
4. NUMPAD 5 — all numbers stop, action bar confirms, survives a relaunch.
5. NUMPAD 6 — screen opens, swatches and hex both apply live.
6. Disable one bucket — only that bucket stops.
7. Meat Shredder or an Epic Fight combo into a crowd — numbers jitter apart, no frame drop.
8. Server log — no `NoClassDefFoundError`, no packet warnings.

- [ ] **Step 2: Document the keybinds**

Add to `docs/keybinds-shabab2.md`:

```markdown
| NUMPAD 5 | Toggle floating damage numbers on/off |
| NUMPAD 6 | Open damage number settings (colours, which buckets show) |
```

Both are rebindable in Options → Controls → Shabab Party.

- [ ] **Step 3: Changelog**

Add to `docs/CHANGELOG.md` under a new `## 1.21.0` heading:

```markdown
### Added
- Floating damage numbers, in three separately-coloured buckets: damage you deal (yellow),
  damage mobs deal to you (red), damage other players deal to you (purple). Each shows the
  weapon's raw roll and the health actually removed, as `raw (final)` — the gap between the
  two is the target's armour.
- NUMPAD 5 toggles all numbers. NUMPAD 6 opens settings: per-bucket colour from five presets
  or any hex value, per-bucket hide, text size, and how long numbers linger. Both keys are
  rebindable in Options → Controls.
```

- [ ] **Step 4: Refresh packwiz**

```bash
cd /c/Minecraft-dev-workspace/Modpacks/pack-two && packwiz refresh
```

Expected: the index picks up `shababparty-1.21.0.jar` and drops the old version.

- [ ] **Step 5: Commit**

```bash
cd /c/Minecraft-dev-workspace/Modpacks
git add docs/keybinds-shabab2.md docs/CHANGELOG.md pack-two/
git commit -m "docs: damage numbers keybinds and 1.21.0 changelog"
```
