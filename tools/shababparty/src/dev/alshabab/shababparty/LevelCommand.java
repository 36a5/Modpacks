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
 * The mod's own /slr level stops at 500 and recalculates stats as it goes. Players here level far past
 * that through play, so admins need a way to set any level. This writes the Level variable only and
 * syncs it to the client; stats and skill points are left as they are, the same as a level that came
 * from play before the stats were spent.
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
            vars.Level = level;
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
}
