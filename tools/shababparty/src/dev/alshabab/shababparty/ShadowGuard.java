package dev.alshabab.shababparty;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stops Solo Leveling's shadow soldiers from crashing the server when their owner is not around.
 *
 * <p>Every shadow (Igris, Beru, Tusk, Kamish, the orcs and goblins: the {@code #minecraft:shadows}
 * entity tag) checks {@code getOwner().isAlive()} on each tick, and its death handler
 * (ShadowDeathReviveProcedure) reads the owner's capabilities. Neither checks for null.
 * {@code getOwner()} is null whenever the owner is offline or in another dimension, so a shadow
 * left behind in the Nether, or one that dies after its owner walks through a portal, throws a
 * NullPointerException. Neruina catches the first one, retries, hits the same null, and crashes
 * the server on purpose (2026-11-08 04:26, an Igris in the Nether).
 *
 * <p>Solo Leveling's own intent for a shadow without a live owner is a puff of smoke and
 * {@code discard()}. This does exactly that, one step earlier, before its code can reach the
 * null: on the shadow's tick (which is skipped) and on its death (which is cancelled, so the
 * revive handler never runs). The owner summons it again from the shadow menu as usual.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShadowGuard {

    private static final TagKey<EntityType<?>> SHADOWS =
            TagKey.m_203882_(Registries.f_256939_, new ResourceLocation("shadows")); // TagKey.create(ENTITY_TYPE, ...)

    private ShadowGuard() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingTick(final LivingEvent.LivingTickEvent event) {
        final LivingEntity entity = event.getEntity();
        if (isOrphanedShadow(entity)) {
            event.setCanceled(true); // skips the shadow's tick, and with it Solo Leveling's owner check
            dismiss(entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(final LivingDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        if (isOrphanedShadow(entity)) {
            event.setCanceled(true); // ShadowDeathReviveProcedure never sees it
            dismiss(entity);
        }
    }

    /** A tamed shadow whose owner cannot be found in this world right now. */
    private static boolean isOrphanedShadow(final LivingEntity entity) {
        if (!(entity instanceof TamableAnimal shadow) || !shadow.m_21824_()) { // isTame
            return false;
        }
        return entity.m_6095_().m_204039_(SHADOWS) // getType().is(SHADOWS)
                && shadow.m_269323_() == null; // getOwner
    }

    /**
     * Server side: the same smoke and removal Solo Leveling uses. Client side: nothing beyond the
     * skipped tick, since the server removes the entity for everyone.
     */
    private static void dismiss(final LivingEntity entity) {
        final Level level = entity.m_9236_(); // level()
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // sendParticles(SMOKE, x, y, z, count, dx, dy, dz, speed)
        server.m_8767_(ParticleTypes.f_123762_, entity.m_20185_(), entity.m_20186_() + 1.0, entity.m_20189_(),
                30, 0.3, 0.6, 0.3, 0.05);
        entity.m_146870_(); // discard
        ShababParty.LOGGER.info("Dismissed a shadow with no owner present ({} at {}, {}, {})",
                entity.m_6095_(), (int) entity.m_20185_(), (int) entity.m_20186_(), (int) entity.m_20189_());
    }
}
