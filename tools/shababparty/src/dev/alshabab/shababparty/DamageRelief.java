package dev.alshabab.shababparty;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Halves (by default) all damage players take from mobs, pack-wide.
 *
 * With the boss progression live, incoming damage was simply too hot - so rather than shaving every
 * boss's damage multiplier one by one, there is one dial on the receiving end. It applies to damage
 * whose owner is a living non-player: mob melee, boss projectiles and breath (DamageSource's entity
 * is the owner, so a fireball counts as its dragon). It stacks multiplicatively with the boss
 * multipliers - a 10x boss hitting through 0.5 lands 5x - and leaves fall damage, lava, drowning and
 * PvP untouched.
 *
 * Tunable live with /scaling set playerDamageTaken <x>.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DamageRelief {

    private DamageRelief() {
    }

    @SubscribeEvent
    public static void onHurt(final LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim) || victim.m_9236_().m_5776_()) {
            return;
        }
        if (!(event.getSource().m_7639_() instanceof LivingEntity attacker) || attacker instanceof Player) {
            return; // not mob damage: environment and PvP stay vanilla
        }
        event.setAmount((float) (event.getAmount() * ShababParty.Config.PLAYER_DAMAGE_TAKEN.get()));
    }
}
