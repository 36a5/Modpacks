package dev.alshabab.shababparty;

import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /track - let an operator watch another player's position on their own minimap and through an ESP
 * box, without the target being told.
 *
 * <ul>
 *   <li>{@code /track <player>} - begin tracking that player.</li>
 *   <li>{@code /track stop}     - stop tracking.</li>
 *   <li>{@code /track}          - report who, if anyone, is being tracked.</li>
 * </ul>
 *
 * <p>Gated to permission level 2 (operators), the same tier vanilla uses for /gamemode and /tp.
 *
 * <p>Every reply here is delivered straight to the tracker with sendSystemMessage, not through
 * {@link CommandSourceStack} success feedback. That matters: command feedback is echoed to other
 * operators when {@code sendCommandFeedback}/admin-command logging is on, which would announce the
 * surveillance to exactly the people it is meant to be hidden from. This keeps it between the server
 * and the one operator who ran it. Nothing is ever sent to the target.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackCommand {

    private TrackCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        // m_82127_ = literal, m_82129_ = argument, m_6761_ = hasPermission. Operators only.
        event.getDispatcher().register(
                Commands.m_82127_("track")
                        .requires(src -> src.m_6761_(2))
                        .executes(TrackCommand::status)
                        .then(Commands.m_82127_("stop")
                                .executes(TrackCommand::stop))
                        .then(Commands.m_82129_("player", EntityArgument.m_91466_()) // player()
                                .executes(TrackCommand::track)));
    }

    private static int track(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerPlayer tracker = ctx.getSource().m_81375_();          // getPlayerOrException
        final ServerPlayer target = EntityArgument.m_91474_(ctx, "player"); // getPlayer

        if (target.m_20148_().equals(tracker.m_20148_())) {                // getUUID
            reply(tracker, "You cannot track yourself.", ChatFormatting.RED);
            return 0;
        }

        TrackManager.start(tracker.m_20148_(), target.m_20148_());
        reply(tracker, "Now tracking " + target.m_6302_()
                + ". They are not notified. Use /track stop to end it.", ChatFormatting.GREEN);
        return 1;
    }

    private static int stop(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerPlayer tracker = ctx.getSource().m_81375_();
        if (TrackManager.stop(tracker.m_20148_())) {
            reply(tracker, "Stopped tracking.", ChatFormatting.YELLOW);
        } else {
            reply(tracker, "You were not tracking anyone.", ChatFormatting.GRAY);
        }
        return 1;
    }

    private static int status(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerPlayer tracker = ctx.getSource().m_81375_();
        final UUID target = TrackManager.targetOf(tracker.m_20148_());
        if (target == null) {
            reply(tracker, "Not tracking anyone. Use /track <player>.", ChatFormatting.GRAY);
            return 1;
        }
        final ServerPlayer online = ctx.getSource().m_81377_().m_6846_().m_11259_(target);
        final String who = online != null ? online.m_6302_() : "an offline player";
        reply(tracker, "Currently tracking " + who + ". Use /track stop to end it.",
                ChatFormatting.AQUA);
        return 1;
    }

    private static void reply(final ServerPlayer to, final String text, final ChatFormatting color) {
        // m_213846_ = sendSystemMessage. Straight to this operator, never broadcast.
        // m_130944_ = withStyle(ChatFormatting...), the varargs form used elsewhere in this mod.
        to.m_213846_(Component.m_237113_(text).m_130944_(color));
    }
}
