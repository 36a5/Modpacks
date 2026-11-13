package dev.alshabab.shababparty;

import java.util.Collection;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.solocraft.network.SololevelingModVariables;

/**
 * /sllevel &lt;players&gt; set|add &lt;amount&gt;: sets a Solo Leveling level with no cap.
 *
 * The mod's own /slr level stops at 500. Players here level far past that through play, so admins need
 * a way to set any level. Each level added or removed is applied the way a real level-up applies it
 * (see {@link #applyLevelChange}), then synced to the client.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LevelCommand {

    private static final int MAX_LEVEL = 1_000_000;

    private LevelCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        // m_82127_ = literal, m_82129_ = argument, m_6761_ = hasPermission, m_91470_ = players().
        event.getDispatcher().register(
                Commands.m_82127_("sllevel")
                        .requires(src -> src.m_6761_(3))
                        .then(Commands.m_82129_("players", EntityArgument.m_91470_())
                                .then(Commands.m_82127_("set")
                                        .then(Commands.m_82129_("amount", IntegerArgumentType.integer(0, MAX_LEVEL))
                                                .executes(ctx -> apply(ctx, false))))
                                .then(Commands.m_82127_("add")
                                        .then(Commands.m_82129_("amount", IntegerArgumentType.integer(-MAX_LEVEL, MAX_LEVEL))
                                                .executes(ctx -> apply(ctx, true))))));
    }

    private static int apply(final CommandContext<CommandSourceStack> ctx, final boolean add)
            throws CommandSyntaxException {
        final Collection<ServerPlayer> players = EntityArgument.m_91477_(ctx, "players"); // getPlayers
        final int amount = IntegerArgumentType.getInteger(ctx, "amount");
        int done = 0;
        for (final ServerPlayer player : players) {
            final SololevelingModVariables.PlayerVariables vars = ((ICapabilityProvider) player)
                    .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                    .orElse(null);
            if (vars == null) {
                continue;
            }
            final double level = Math.max(0, Math.min(MAX_LEVEL, add ? vars.Level + amount : amount));
            applyLevelChange(vars, (int) vars.Level, (int) level);
            vars.syncPlayerVariables(player);
            final String text = player.m_7755_().getString() + " is now level " + (long) level;
            ctx.getSource().m_288197_(() -> Component.m_237113_(text), true); // sendSuccess
            done++;
        }
        if (done == 0) {
            ctx.getSource().m_81352_(Component.m_237113_("No Solo Leveling data on those players.")); // sendFailure
        }
        return done;
    }

    /**
     * Does what Solo Leveling's own LevelUpProcedure does for the same number of levels, so a level set
     * here is worth exactly what a level earned through XP is worth:
     * <ul>
     * <li>+1 Vitality, Strength, Intelligence, Perception, Speed and Durability per level. The mod
     * distributes these itself; a level-up grants no free skill points.</li>
     * <li>Hunter rank +1 for every multiple of 25 crossed, capped at 6, the mod's top rank.</li>
     * <li>Fatigue cleared on the way up.</li>
     * </ul>
     * Going down reverses it: each stat loses one per level (never below 0) and the rank drops by the
     * multiples of 25 crossed (never below 0). Free skill points are left alone in both directions.
     */
    static void applyLevelChange(final SololevelingModVariables.PlayerVariables vars, final int from, final int to) {
        final int delta = to - from;
        vars.Level = to;
        if (delta == 0) {
            return;
        }
        vars.Vitality = Math.max(0.0D, vars.Vitality + delta);
        vars.Strength = Math.max(0.0D, vars.Strength + delta);
        vars.Intelligence = Math.max(0.0D, vars.Intelligence + delta);
        vars.perception = Math.max(0.0D, vars.perception + delta);
        vars.Speed = Math.max(0.0D, vars.Speed + delta);
        vars.Durability = Math.max(0.0D, vars.Durability + delta);
        final int rankSteps = to / 25 - from / 25;
        vars.HunterRank = Math.max(0.0D, Math.min(6.0D, vars.HunterRank + rankSteps));
        if (delta > 0) {
            vars.Fatigue = 0.0D;
        }
    }
}
