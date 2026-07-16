package dev.alshabab.shababparty;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.solocraft.network.SololevelingModVariables;

/**
 * /invest [amount] - choose how many stat points each "+" click in the Solo Leveling panel spends.
 *
 * The panel's + buttons already spend a per-player variable, investvalue (every *IncreaseProcedure
 * checks SkillPoints against it and moves exactly that much). It just defaults to 1 and the mod gives
 * players no good way to raise it - which turns a Reborn Elixir refund of 600 SP into 600 clicks.
 * This command writes that variable directly, so the mod's own panel does the rest.
 *
 * No permission gate: it is a per-player quality-of-life setting, like a chat preference.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InvestCommand {

    private InvestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        // m_82127_ = literal, m_82129_ = argument. No .requires - every player may use it.
        event.getDispatcher().register(
                Commands.m_82127_("invest")
                        .executes(InvestCommand::show)
                        .then(Commands.m_82129_("amount", IntegerArgumentType.integer(1, 100000))
                                .executes(InvestCommand::set)));
    }

    private static int show(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().m_81375_(); // getPlayerOrException
        final SololevelingModVariables.PlayerVariables vars = varsOf(player);
        if (vars != null) {
            player.m_5661_(Component.m_237113_("Each + click currently invests "
                            + (int) vars.investvalue + " point(s). Change it with /invest <amount>.")
                    .m_130944_(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int set(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().m_81375_();
        final SololevelingModVariables.PlayerVariables vars = varsOf(player);
        if (vars == null) {
            return 0;
        }
        final int amount = IntegerArgumentType.getInteger(ctx, "amount");
        vars.investvalue = amount;
        vars.syncPlayerVariables(player);
        player.m_5661_(Component.m_237113_("Each + click now invests " + amount + " point(s).")
                .m_130944_(ChatFormatting.GOLD), false);
        return 1;
    }

    private static SololevelingModVariables.PlayerVariables varsOf(final ServerPlayer player) {
        return ((ICapabilityProvider) player)
                .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                .orElse(null);
    }
}
