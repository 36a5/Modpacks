package dev.alshabab.shababparty.mixin;

import dev.alshabab.shababparty.Ability4;
import dev.alshabab.shababparty.ShababParty;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.solocraft.network.SololevelingModVariables;
import net.solocraft.procedures.Ability4OnKeyPressedProcedure;

/**
 * Replaces the two "ability wip" stubs on the V key with real abilities.
 *
 * Ability4OnKeyPressedProcedure branches on JOB. Grand Mage (2) and Monarch of White Flames (4)
 * both end in:
 *
 *     if (!player.level().isClientSide)
 *         player.displayClientMessage(Component.literal("ability wip"), false);
 *
 * and nothing else - no mana, no cooldown, no effect.
 *
 * The redirect is on displayClientMessage rather than an @Inject(HEAD, cancellable) on execute,
 * because HEAD-cancelling would also skip whatever the method does *before* the branch (the ability
 * flags, the mana checks that the finished jobs rely on). Swapping only the payload of the dead
 * branch cannot affect Shadow Monarch or Frost Monarch at all.
 *
 * There are three displayClientMessage calls in the method: these two, and Shadow Monarch's
 * "Activated Aura". Dispatch is on JOB rather than on matching the "ability wip" text, so the aura
 * message is forwarded untouched and nothing depends on a mod string that could be typo-fixed later.
 *
 * remap = false throughout: Solo Leveling's classes are not obfuscated, so "execute" is its real
 * name, while the redirect target is a Minecraft method and is written in SRG (m_5661_ =
 * displayClientMessage) because shababparty compiles straight against the production jars.
 */
@Mixin(value = Ability4OnKeyPressedProcedure.class, remap = false)
public abstract class Ability4WipMixin {

    private static final double JOB_GRAND_MAGE = 2.0D;
    private static final double JOB_WHITE_FLAMES = 4.0D;

    @Redirect(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;m_5661_(Lnet/minecraft/network/chat/Component;Z)V",
                    remap = false),
            remap = false)
    private static void shababparty$fillInWipAbility(final Player player, final Component message, final boolean actionBar) {
        if (player instanceof ServerPlayer server) {
            // Cast to ICapabilityProvider first: Forge patches getCapability onto Entity as the game
            // boots, so the vanilla jar we compile against has no such method. Same reason, and the
            // same workaround, as PartySupport.capabilityOf.
            final SololevelingModVariables.PlayerVariables vars =
                    ((ICapabilityProvider) player)
                            .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                            .orElse(null);

            String ability = null;
            if (vars != null) {
                if (vars.JOB == JOB_WHITE_FLAMES) {
                    ability = ShababParty.Config.WHITE_FLAMES_ABILITY_4.get();
                } else if (vars.JOB == JOB_GRAND_MAGE) {
                    ability = ShababParty.Config.GRAND_MAGE_ABILITY_4.get();
                }
            }

            if (ability != null && !Ability4.NONE.equals(ability) && Ability4.cast(ability, server)) {
                return;
            }
        }

        // Any other job - Shadow Monarch's "Activated Aura" - or an ability set to "none".
        player.m_5661_(message, actionBar);
    }
}
