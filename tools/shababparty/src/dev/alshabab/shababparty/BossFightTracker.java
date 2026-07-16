package dev.alshabab.shababparty;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Remembers whether anyone died fighting a boss, so a deathless kill can pay extra.
 *
 * When a player dies, every scaled boss within {@link #RADIUS} of the corpse is marked tainted. When
 * a boss dies, {@link BossLevels} and {@link BossLoot} ask {@link #isClean}; the last of them
 * ({@link BossLoot}, whose LivingDropsEvent fires after the death event) calls {@link #forget}.
 *
 * State is in memory only: a fight that spans a server restart forgets its deaths and counts as
 * clean, which errs on the generous side and keeps this to one map instead of world storage. The map
 * cannot grow without bound - entries are removed when their boss dies, and only bosses that ever
 * witnessed a player death are in it at all.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossFightTracker {

    private static final double RADIUS = 64.0D;
    private static final double RADIUS_SQ = RADIUS * RADIUS;

    /** Bosses that saw a party member die. Presence = tainted. */
    private static final Map<UUID, Boolean> TAINTED = new ConcurrentHashMap<>();

    private BossFightTracker() {
    }

    @SubscribeEvent
    public static void onPlayerDeath(final LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.m_9236_() instanceof ServerLevel level)) { // level()
            return;
        }
        // Player deaths are rare enough that a full entity scan on each one is fine.
        for (final Entity entity : level.m_8583_()) { // getAllEntities
            if (entity instanceof LivingEntity boss
                    && BossScaling.isScaled(boss)
                    && player.m_20280_(boss) <= RADIUS_SQ) { // distanceToSqr
                TAINTED.put(boss.m_20148_(), Boolean.TRUE); // getUUID
            }
        }
    }

    /** True when nobody died within {@link #RADIUS} of this boss since it spawned (or last restart). */
    static boolean isClean(final LivingEntity boss) {
        return !TAINTED.containsKey(boss.m_20148_());
    }

    /** Called by the last reader ({@link BossLoot}) once the boss's rewards are settled. */
    static void forget(final LivingEntity boss) {
        TAINTED.remove(boss.m_20148_());
    }
}
