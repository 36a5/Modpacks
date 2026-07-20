package dev.alshabab.shababparty;

import java.util.HashMap;
import java.util.Map;

import dev.alshabab.shababparty.network.DamageNumberPacket;
import dev.alshabab.shababparty.network.Net;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Turns damage into packets.
 *
 * <h2>Why two events</h2>
 * Neither Forge event carries both numbers the feature needs. LivingHurtEvent has the incoming
 * amount before armour and enchantment reduction; LivingDamageEvent has the final amount that will
 * leave the health bar. They fire in that order within one tick for the same victim, so the raw
 * value is stashed by entity id and consumed by the second event.
 *
 * <h2>Why both handlers are LOWEST priority</h2>
 * This mod already mutates damage in three places, all at default priority: {@link PlayerPower}
 * multiplies LivingHurtEvent by the Solo Leveling Strength stat, and {@link BossScaling} and
 * {@link DamageRelief} both scale LivingDamageEvent. Forge does not order same-priority handlers
 * deterministically, so reading the amount at NORMAL would sometimes see PlayerPower's multiplier
 * and sometimes not -- the number on screen would disagree with the damage dealt, at random, which
 * is worse than showing nothing at all.
 *
 * <p>LOWEST runs last. Both reads therefore observe the fully-modified value: weapon base damage,
 * enchantments, Epic Fight's combat maths, the Solo Leveling Strength multiplier, boss scaling and
 * damage relief, all already applied. Nothing here recomputes damage from weapon stats or player
 * level -- it reports what the pipeline produced, which is the only figure that can be trusted to
 * match what actually happened.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID)
public final class DamageNumbers {

    private static final Map<Integer, Float> RAW = new HashMap<>();

    private DamageNumbers() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(final LivingHurtEvent event) {
        if (event.getEntity().m_9236_().m_5776_()) {
            return;
        }
        RAW.put(event.getEntity().m_19879_(), event.getAmount());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(final LivingDamageEvent event) {
        final LivingEntity victim = event.getEntity();
        if (victim.m_9236_().m_5776_()) {
            return;
        }

        final int victimId = victim.m_19879_();
        final Float stashed = RAW.remove(victimId);
        final float finalAmount = event.getAmount();

        // A stream of "0" popups from an immune boss phase is noise, not information.
        if (finalAmount <= 0.0F) {
            return;
        }
        final float rawAmount = stashed == null ? finalAmount : stashed;

        final Entity attacker = event.getSource().m_7639_();
        final boolean selfInflicted = attacker == victim;

        if (!selfInflicted && attacker instanceof ServerPlayer dealer) {
            Net.toPlayer(dealer, new DamageNumberPacket(
                    victimId, rawAmount, finalAmount, DamageNumberPacket.OUTGOING));
        }

        if (victim instanceof ServerPlayer target) {
            // Self-inflicted damage counts as generic incoming rather than PvP -- being set alight
            // by your own Fire Aspect is not another player hitting you.
            final int bucket = (!selfInflicted && attacker instanceof Player)
                    ? DamageNumberPacket.PLAYER_TO_YOU
                    : DamageNumberPacket.MOB_TO_YOU;
            Net.toPlayer(target, new DamageNumberPacket(victimId, rawAmount, finalAmount, bucket));
        }
    }

    /**
     * A LivingDamageEvent cancelled by another mod leaves its raw value stashed with nothing to
     * consume it. Hurt and damage always pair within one tick, so anything still here at end of tick
     * is orphaned, and clearing wholesale is both correct and cheaper than tracking ages.
     */
    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !RAW.isEmpty()) {
            RAW.clear();
        }
    }
}
