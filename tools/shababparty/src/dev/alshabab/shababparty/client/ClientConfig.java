package dev.alshabab.shababparty.client;

import dev.alshabab.shababparty.ShababParty;
import dev.alshabab.shababparty.network.DamageNumberPacket;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Display settings for the floating damage numbers.
 *
 * <p>This is Type.CLIENT, not COMMON like {@link ShababParty.Config}: these are one player's
 * preferences about what their own screen looks like. Putting them in COMMON would write them to
 * the server's config directory and force every player on the server to share one colour scheme.
 *
 * <p>Colours are stored as bare six-digit hex with no leading '#', because '#' opens a comment in
 * TOML and would have to be quoted on every hand edit. {@link #parseColor} accepts either form.
 *
 * <p>Despite living in the client package this class holds no net.minecraft.client types, so it is
 * safe to name from common code -- which is what lets ShababParty register the spec directly.
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
    public static final ForgeConfigSpec.BooleanValue ALLY_TO_MOB_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> ALLY_TO_MOB_COLOR;
    public static final ForgeConfigSpec.BooleanValue SHOW_RAW;
    public static final ForgeConfigSpec.IntValue LIFETIME_TICKS;
    public static final ForgeConfigSpec.DoubleValue SCALE;
    public static final ForgeConfigSpec.DoubleValue RISE_SPEED;
    public static final ForgeConfigSpec.IntValue MAX_POPUPS;

    static {
        final ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
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

        b.comment("Damage other players deal to mobs near you.").push("allyToMob");
        ALLY_TO_MOB_ENABLED = b.define("enabled", true);
        ALLY_TO_MOB_COLOR = b.comment("Six hex digits, no '#'.").define("color", "55FFFF");
        b.pop();

        SHOW_RAW = b.comment(
                        "true  = the weapon's roll, before the target's armour reduces it.",
                        "false = the health actually removed.",
                        "Whichever is chosen is also what the combo total sums.")
                .define("showRaw", true);
        LIFETIME_TICKS = b.comment(
                        "How long a number stays on screen after its last hit, in ticks. 20 = one second.",
                        "This is also the combo window: further hits on the same target while the",
                        "number is still up add to its total instead of starting a new one.")
                .defineInRange("lifetimeTicks", 40, 5, 200);
        SCALE = b.comment("Text size multiplier. Numbers are drawn at HUD scale, so this is absolute.")
                .defineInRange("scale", 2.5D, 0.25D, 8.0D);
        RISE_SPEED = b.comment("Blocks per second the number drifts upward.")
                .defineInRange("riseSpeed", 0.35D, 0.0D, 1.0D);
        MAX_POPUPS = b.comment("Hard cap on simultaneous numbers. The oldest is dropped at the cap.")
                .defineInRange("maxPopups", 64, 8, 256);

        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {
    }

    /** 0xRRGGBB for a bucket, falling back to that bucket's default if the config holds junk. */
    public static int colorOf(final int bucket) {
        switch (bucket) {
            case DamageNumberPacket.MOB_TO_YOU:
                return parseColor(MOB_TO_YOU_COLOR.get(), 0xFF5555);
            case DamageNumberPacket.PLAYER_TO_YOU:
                return parseColor(PLAYER_TO_YOU_COLOR.get(), 0xFF55FF);
            case DamageNumberPacket.ALLY_TO_MOB:
                return parseColor(ALLY_TO_MOB_COLOR.get(), 0x55FFFF);
            default:
                return parseColor(OUTGOING_COLOR.get(), 0xFFFF55);
        }
    }

    public static boolean bucketEnabled(final int bucket) {
        switch (bucket) {
            case DamageNumberPacket.MOB_TO_YOU:
                return MOB_TO_YOU_ENABLED.get();
            case DamageNumberPacket.PLAYER_TO_YOU:
                return PLAYER_TO_YOU_ENABLED.get();
            case DamageNumberPacket.ALLY_TO_MOB:
                return ALLY_TO_MOB_ENABLED.get();
            default:
                return OUTGOING_ENABLED.get();
        }
    }

    /**
     * A hand-edited config file is the one place a bad colour can enter. Warn and fall back rather
     * than throwing: a malformed hex string must not take the renderer down mid-fight.
     */
    public static int parseColor(final String raw, final int fallback) {
        if (raw == null) {
            return fallback;
        }
        final String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (final NumberFormatException e) {
            ShababParty.LOGGER.warn("damage numbers: '{}' is not a hex colour, using default", raw);
            return fallback;
        }
    }
}
