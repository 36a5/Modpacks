package dev.alshabab.shababparty;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.solocraft.network.SololevelingModVariables;

/**
 * Makes the Solo Leveling Strength stat actually raise the damage a player deals.
 *
 * <h2>Why this is needed</h2>
 * Solo Leveling's StrengthUpdateProcedure does set generic.attack_damage (base = 1 + Strength*0.075,
 * uncapped) - so the stat menu is not cosmetic at the attribute level. But Epic Fight is installed and
 * runs its own melee combat: it manages the attack_damage attribute per weapon, normalising a hit to
 * the weapon's own numbers, so an inflated base does NOT flow through to the damage a swing actually
 * deals. A player with 10,000 Strength felt exactly as strong as one with 100.
 *
 * <h2>The fix</h2>
 * Scale the FINAL damage on LivingHurtEvent, the amount after every mod - Epic Fight included - has
 * had its say. The multiplier is 1 + Strength * strengthDamagePerPoint, so Strength always matters
 * whatever the weapon or combat mod. This mirrors how BossScaling scales boss damage and DamageRelief
 * scales incoming damage: intercept the number that lands, not an attribute upstream of it.
 *
 * Only the owner's own hits are scaled (source entity is the player); shadow-soldier damage is scaled
 * separately by {@link ShadowScaling} off Intelligence.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerPower {

    private PlayerPower() {
    }

    @SubscribeEvent
    public static void onHurt(final LivingHurtEvent event) {
        if (!ShababParty.Config.PLAYER_POWER_ENABLED.get()) {
            return;
        }
        if (!(event.getSource().m_7639_() instanceof Player player) || player.m_9236_().m_5776_()) {
            return; // not a player's hit, or client side
        }

        final SololevelingModVariables.PlayerVariables vars =
                ((ICapabilityProvider) player)
                        .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                        .orElse(null);
        if (vars == null) {
            return;
        }

        final double strength = Math.max(0.0D, vars.Strength);
        final double multiplier = 1.0D + strength * ShababParty.Config.STRENGTH_DAMAGE_PER_POINT.get();
        if (multiplier != 1.0D) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }
}
