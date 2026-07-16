package dev.alshabab.shababparty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /scaling - retune the boss and shadow numbers from chat, on a live server, no restart.
 *
 *   /scaling get                              every knob and its current value
 *   /scaling set <knob> <value>               one of the flat knobs below
 *   /scaling tier <boss>                      one boss's health / damage / level reward
 *   /scaling tier <boss> <hp> <dmg> <levels>  set them (adds the boss if it has no entry)
 *
 * Writing goes through ForgeConfigSpec.ConfigValue.set(), which updates the running value AND saves
 * the config file, so a change survives the next restart - this is editing the config, not a
 * temporary override. Flat knobs are read fresh on every event, so they apply immediately; the parsed
 * tier map is cached, so tier writes invalidate it ({@link BossScaling#invalidateTiers}).
 *
 * What a tier change does NOT do: re-scale a boss that is already spawned. Its health modifier is
 * persisted in its NBT and applied once, deliberately (see BossScaling on idempotency). New spawns
 * get the new numbers. Shadows, by contrast, re-sync on a timer, so shadow knobs visibly apply to a
 * standing army within seconds.
 *
 * Permission level 3, same as /stat and /slr.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ScalingCommand {

    /** One tunable: how to read it, how to write it from a double. Insertion order is display order. */
    private record Knob(Supplier<Object> get, Consumer<Double> set) {
    }

    private static final Map<String, Knob> KNOBS = new LinkedHashMap<>();

    static {
        KNOBS.put("bossHealthMultiplier", new Knob(
                () -> ShababParty.Config.BOSS_HEALTH_MULTIPLIER.get(),
                v -> ShababParty.Config.BOSS_HEALTH_MULTIPLIER.set(v)));
        KNOBS.put("bossDamageMultiplier", new Knob(
                () -> ShababParty.Config.BOSS_DAMAGE_MULTIPLIER.get(),
                v -> ShababParty.Config.BOSS_DAMAGE_MULTIPLIER.set(v)));
        KNOBS.put("levelsPer10kHp", new Knob(
                () -> ShababParty.Config.LEVELS_PER_10K_HP.get(),
                v -> ShababParty.Config.LEVELS_PER_10K_HP.set(v)));
        KNOBS.put("playerDamageTaken", new Knob(
                () -> ShababParty.Config.PLAYER_DAMAGE_TAKEN.get(),
                v -> ShababParty.Config.PLAYER_DAMAGE_TAKEN.set(v)));
        KNOBS.put("milestoneMultiplier", new Knob(
                () -> ShababParty.Config.MILESTONE_MULTIPLIER.get(),
                v -> ShababParty.Config.MILESTONE_MULTIPLIER.set(v)));
        KNOBS.put("noDeathBonus", new Knob(
                () -> ShababParty.Config.NO_DEATH_BONUS.get(),
                v -> ShababParty.Config.NO_DEATH_BONUS.set(v)));
        KNOBS.put("announceHpThreshold", new Knob(
                () -> ShababParty.Config.ANNOUNCE_HP_THRESHOLD.get(),
                v -> ShababParty.Config.ANNOUNCE_HP_THRESHOLD.set(v)));
        KNOBS.put("spBountyThreshold", new Knob(
                () -> ShababParty.Config.SP_BOUNTY_THRESHOLD.get(),
                v -> ShababParty.Config.SP_BOUNTY_THRESHOLD.set(v)));
        KNOBS.put("spPer10kHp", new Knob(
                () -> ShababParty.Config.SP_PER_10K_HP.get(),
                v -> ShababParty.Config.SP_PER_10K_HP.set(v)));
        KNOBS.put("lootDropMultiplier", new Knob(
                () -> ShababParty.Config.BOSS_LOOT_DROP_MULTIPLIER.get(),
                v -> ShababParty.Config.BOSS_LOOT_DROP_MULTIPLIER.set(v.intValue())));
        KNOBS.put("lootBonusEnchantLevels", new Knob(
                () -> ShababParty.Config.BOSS_LOOT_BONUS_ENCHANT_LEVELS.get(),
                v -> ShababParty.Config.BOSS_LOOT_BONUS_ENCHANT_LEVELS.set(v.intValue())));
        KNOBS.put("lootEnchantLevel", new Knob(
                () -> ShababParty.Config.BOSS_LOOT_ENCHANT_LEVEL.get(),
                v -> ShababParty.Config.BOSS_LOOT_ENCHANT_LEVEL.set(v.intValue())));
        KNOBS.put("lootXpMultiplier", new Knob(
                () -> ShababParty.Config.BOSS_LOOT_XP_MULTIPLIER.get(),
                v -> ShababParty.Config.BOSS_LOOT_XP_MULTIPLIER.set(v.intValue())));
        KNOBS.put("shadowEliteFraction", new Knob(
                () -> ShababParty.Config.SHADOW_ELITE_FRACTION.get(),
                v -> ShababParty.Config.SHADOW_ELITE_FRACTION.set(v)));
        KNOBS.put("shadowStandardFraction", new Knob(
                () -> ShababParty.Config.SHADOW_STANDARD_FRACTION.get(),
                v -> ShababParty.Config.SHADOW_STANDARD_FRACTION.set(v)));
        KNOBS.put("shadowDamagePerIntelligence", new Knob(
                () -> ShababParty.Config.SHADOW_DAMAGE_PER_INTELLIGENCE.get(),
                v -> ShababParty.Config.SHADOW_DAMAGE_PER_INTELLIGENCE.set(v)));
    }

    private static final SimpleCommandExceptionType UNKNOWN_KNOB = new SimpleCommandExceptionType(
            Component.m_237113_("Unknown knob. Valid: " + String.join(", ", KNOBS.keySet())));

    private ScalingCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        // m_82127_ = literal, m_82129_ = argument, m_6761_ = hasPermission. Level 3, like /stat.
        event.getDispatcher().register(
                Commands.m_82127_("scaling")
                        .requires(src -> src.m_6761_(3))
                        .then(Commands.m_82127_("get")
                                .executes(ScalingCommand::getAll))
                        .then(Commands.m_82127_("set")
                                .then(Commands.m_82129_("knob", StringArgumentType.word())
                                        .then(Commands.m_82129_("value", DoubleArgumentType.doubleArg(0.0D))
                                                .executes(ScalingCommand::setKnob))))
                        .then(Commands.m_82127_("tier")
                                .then(Commands.m_82129_("boss", ResourceLocationArgument.m_106984_()) // id()
                                        .executes(ScalingCommand::getTier)
                                        .then(Commands.m_82129_("hp", DoubleArgumentType.doubleArg(1.0D))
                                                .then(Commands.m_82129_("dmg", DoubleArgumentType.doubleArg(0.0D))
                                                        .then(Commands.m_82129_("levels", IntegerArgumentType.integer(0))
                                                                .executes(ScalingCommand::setTier)))))));
    }

    private static int getAll(final CommandContext<CommandSourceStack> ctx) {
        final StringBuilder out = new StringBuilder("Scaling knobs:");
        KNOBS.forEach((name, knob) -> out.append("\n  ").append(name).append(" = ").append(knob.get().get()));
        out.append("\n  bossTiers: ").append(ShababParty.Config.BOSS_TIERS.get().size())
                .append(" entries - /scaling tier <boss> to inspect one");
        ctx.getSource().m_288197_(() -> Component.m_237113_(out.toString()), false); // sendSuccess
        return 1;
    }

    private static int setKnob(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final String name = StringArgumentType.getString(ctx, "knob");
        final Knob knob = KNOBS.get(name);
        if (knob == null) {
            throw UNKNOWN_KNOB.create();
        }
        final double value = DoubleArgumentType.getDouble(ctx, "value");
        knob.set().accept(value); // ConfigValue.set: applies now AND writes the config file
        ctx.getSource().m_288197_(() -> Component.m_237113_(name + " = " + knob.get().get()
                + " (saved to config; flat knobs apply to the next event, shadows re-sync within seconds)"), true);
        return 1;
    }

    private static int getTier(final CommandContext<CommandSourceStack> ctx) {
        final ResourceLocation boss = ResourceLocationArgument.m_107011_(ctx, "boss"); // getId
        final String prefix = boss + "=";
        final String entry = ShababParty.Config.BOSS_TIERS.get().stream()
                .filter(line -> line.trim().startsWith(prefix))
                .findFirst().orElse(null);
        ctx.getSource().m_288197_(() -> Component.m_237113_(entry != null
                ? entry + "   (health, damage multiplier, level reward)"
                : boss + " has no tier entry - it uses the flat multipliers. Set one with /scaling tier "
                        + boss + " <hp> <dmg> <levels>"), false);
        return 1;
    }

    private static int setTier(final CommandContext<CommandSourceStack> ctx) {
        final ResourceLocation boss = ResourceLocationArgument.m_107011_(ctx, "boss");
        final double hp = DoubleArgumentType.getDouble(ctx, "hp");
        final double dmg = DoubleArgumentType.getDouble(ctx, "dmg");
        final int levels = IntegerArgumentType.getInteger(ctx, "levels");

        final String prefix = boss + "=";
        final String entry = boss + "=" + trim(hp) + "," + trim(dmg) + "," + levels;

        final List<String> tiers = new ArrayList<>(ShababParty.Config.BOSS_TIERS.get());
        tiers.removeIf(line -> line.trim().startsWith(prefix));
        tiers.add(entry);
        ShababParty.Config.BOSS_TIERS.set(tiers); // applies now AND writes the config file
        BossScaling.invalidateTiers();

        ctx.getSource().m_288197_(() -> Component.m_237113_(entry
                + " (saved; applies to newly spawned " + boss + " - already-spawned ones keep their number)"), true);
        return 1;
    }

    /** 8000.0 -> "8000", 0.5 -> "0.5": keep the config file readable. */
    private static String trim(final double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
