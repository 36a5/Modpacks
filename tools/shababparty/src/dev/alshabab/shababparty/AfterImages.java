package dev.alshabab.shababparty;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.solocraft.entity.AfterImageEntity;
import net.solocraft.init.SololevelingModEntities;
import net.solocraft.procedures.AfterImageOnInitialEntitySpawnProcedure;

/**
 * The whole life of Solo Leveling's after-image, which the mod itself never quite finished.
 *
 * <h2>The leak</h2>
 * AfterImageEntity's despawn is not on the entity. It lives in
 * AfterImageOnInitialEntitySpawnProcedure - no gravity, an entity.wither.shoot cue, and a
 * queueServerWork(10, () -> discard()) - and that procedure is reached from exactly one place:
 * AfterImageEntity.finalizeSpawn. Level.addFreshEntity does not call finalizeSpawn. Only natural
 * spawning, spawn eggs and EntityType.spawn do.
 *
 * So an after-image spawned the ordinary way never despawns. And AfterImageEntity extends Monster
 * and implements GeoEntity: each leaked one is a ticking mob with a GeckoLib rig that gets *written
 * into chunk NBT*, so it comes back every session. They accumulate without bound. That is both the
 * "the clones never vanish" report and a large part of the out-of-memory kicks.
 *
 * <h2>Two layers</h2>
 * {@link #spawn} calls the mod's own procedure, so a new after-image behaves exactly as Solo
 * Leveling intended: it poofs at 10 ticks with particles.
 *
 * {@link #onJoin} is the guarantee. queueServerWork is a scheduled runnable that a restart drops on
 * the floor, and it does nothing whatever about the after-images already sitting in players' region
 * files. So every AfterImageEntity entering a *server* level - ours, someone else's, or one loading
 * off disk from before this fix - is put on a deadline and discarded when it expires. Existing
 * worlds heal themselves as players walk around: no world edit, no /kill, no reset.
 *
 * The tracking list cannot become the next leak. Every entry leaves it within the deadline, or
 * sooner if the entity is already gone.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AfterImages {

    /** After-images awaiting their deadline. Server thread only. */
    private static final List<Tracked> TRACKED = new ArrayList<>();

    private record Tracked(AfterImageEntity entity, long deadline) {
    }

    /** Leave an after-image where the player was standing. Cosmetic: failure must not cost the counter. */
    public static void spawn(final ServerLevel level, final ServerPlayer player, final Vec3 at) {
        try {
            final EntityType<AfterImageEntity> type = SololevelingModEntities.AFTER_IMAGE.get();
            final AfterImageEntity image = type.m_20615_(level);
            if (image == null) {
                return;
            }
            image.m_7678_(at.m_7096_(), at.m_7098_(), at.m_7094_(), player.m_146908_(), 0.0F);
            level.m_7967_(image);

            // The line whose absence was the bug. onJoin has already put this entity on a deadline
            // by the time addFreshEntity returns, so even if this throws, it still despawns.
            AfterImageOnInitialEntitySpawnProcedure.execute(
                    level, at.m_7096_(), at.m_7098_(), at.m_7094_(), image);
        } catch (final Throwable ignored) {
            // A missing or changed after-image entity must not cost the player his counter.
        }
    }

    @SubscribeEvent
    public static void onJoin(final EntityJoinLevelEvent event) {
        if (event.getLevel().m_5776_()) {
            return;
        }
        if (!(event.getEntity() instanceof AfterImageEntity image)) {
            return;
        }
        final long deadline = event.getLevel().m_46467_()
                + ShababParty.Config.AFTER_IMAGE_LIFETIME_TICKS.get();
        TRACKED.add(new Tracked(image, deadline));
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRACKED.isEmpty()) {
            return;
        }
        final Iterator<Tracked> images = TRACKED.iterator();
        while (images.hasNext()) {
            final Tracked tracked = images.next();
            final AfterImageEntity image = tracked.entity();

            // Already gone: the mod's own 10-tick despawn fired, or the chunk unloaded.
            if (image.m_213877_()) {
                images.remove();
                continue;
            }
            // Read the time off the entity's own level rather than the event: an after-image can be
            // in any dimension, and each level keeps its own game time.
            if (image.m_9236_().m_46467_() >= tracked.deadline()) {
                image.m_146870_();
                images.remove();
            }
        }
    }

    private AfterImages() {
    }
}
