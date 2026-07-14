package dev.alshabab.shababparty;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.solocraft.init.SololevelingModMobEffects;
import net.solocraft.network.SololevelingModVariables;

/**
 * Removes the cooldown on Monarch of White Flames' C ability.
 *
 * Solo Leveling does not implement job-ability cooldowns as a timer. Ability3OnKeyPressedProcedure's
 * JOB == 4 branch reads:
 *
 *     if (!(entity instanceof LivingEntity le && le.hasEffect(JOB_COOLDOWN_3)))
 *         StormBurstProcedure.execute(...);
 *
 * and StormBurstProcedure's first act is to put JOB_COOLDOWN_3 on the caster for 200 ticks. The
 * cooldown *is* the mob effect. Stop the effect landing and the gate never closes.
 *
 * MobEffectEvent.Applicable rather than a mixin on StormBurstProcedure: Forge's
 * LivingEntity.canBeAffected posts this event and honours DENY, and addEffect goes through
 * canBeAffected - so this is the supported way to refuse an effect, with no bytecode surgery. It is
 * also the narrower fix. All four jobs share JOB_COOLDOWN_3 for their own C ability, and dispatching
 * on JOB == 4 leaves the other three exactly as they were; a redirect inside StormBurstProcedure
 * would instead uncap the ability for anyone who ever came to call it.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JobCooldown {

    private static final double JOB_WHITE_FLAMES = 4.0D;

    @SubscribeEvent
    public static void onEffectApplicable(final MobEffectEvent.Applicable event) {
        if (ShababParty.Config.WHITE_FLAMES_ABILITY_3_COOLDOWN.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getEffectInstance().m_19544_() != SololevelingModMobEffects.JOB_COOLDOWN_3.get()) {
            return;
        }

        // Cast to ICapabilityProvider first: Forge patches getCapability onto Entity as the game
        // boots, so the vanilla jar we compile against has no such method. Same reason, and the same
        // workaround, as Ability4WipMixin and PartySupport.capabilityOf.
        final SololevelingModVariables.PlayerVariables vars =
                ((ICapabilityProvider) player)
                        .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                        .orElse(null);

        if (vars != null && vars.JOB == JOB_WHITE_FLAMES) {
            event.setResult(Event.Result.DENY);
        }
    }

    private JobCooldown() {
    }
}
