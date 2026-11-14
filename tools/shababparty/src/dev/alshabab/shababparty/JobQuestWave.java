package dev.alshabab.shababparty;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.solocraft.entity.DKnight1Entity;
import net.solocraft.entity.DKnight2Entity;
import net.solocraft.entity.DKnight3Entity;
import net.solocraft.entity.SpawnerPortalEntity;
import net.solocraft.init.SololevelingModEntities;
import net.solocraft.network.SololevelingModVariables;

/**
 * Turns the Job Change Quest's survival phase into one wave you can clear.
 *
 * In Solo Leveling, killing Igris starts a 100 second survival (PlayerVariables.jobtimer = 100).
 * Spawner portals appear on the dungeon's unbreakable deepslate, and each one has a 1% chance per
 * tick to drop a Demon Knight until it expires. Killing knights does not shorten anything: the
 * quest only ends when JobAdvTimerProcedure counts jobtimer down to 1, so every run is a full
 * 100 seconds of waiting for knights to trickle out.
 *
 * Here, as soon as a player in the Igris dungeon has the survival running, every spawner portal
 * near them is swapped for its knights all at once (KNIGHTS_PER_PORTAL each, glowing so stragglers
 * are easy to find). When no knight and no portal is left near the player, jobtimer is dropped to
 * 2. JobAdvTimerProcedure takes it to 1 on its next once-a-second step and finishes the quest
 * through its own path (instance complete, leftover mobs cleared, cutscene started), so nothing
 * about the ending is reimplemented here. If the wave is never cleared the original 100 second
 * timer still runs out as before.
 *
 * Party runs: Igris's death starts the timer for every party member in the dungeon. Whoever ticks
 * first converts the portals; everyone's own check then sees the same knights, so they all finish
 * together. A player only gets the early finish once a few seconds have passed (CLEAR_CHECK_BELOW),
 * which leaves time for the portals to be converted before "nothing left" can be true.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JobQuestWave {

    private static final String IGRIS_DIMENSION = "sololeveling:dungeon_dimension_igris";
    private static final int KNIGHTS_PER_PORTAL = 3;
    private static final double RANGE = 150.0D;
    /** jobtimer starts at 100 and counts down once per second; check for a clear below this. */
    private static final double CLEAR_CHECK_BELOW = 97.0D;
    /** One second above the mod's finish value, so its own countdown step ends the quest. */
    private static final double FINISH_VALUE = 2.0D;
    private static final int GLOW_TICKS = 20 * 120;

    private JobQuestWave() {
    }

    @SubscribeEvent
    public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        // Once every 10 ticks is plenty for a wave check and keeps the entity scans cheap.
        if ((player.f_19797_ % 10) != 0) { // tickCount
            return;
        }
        final ServerLevel level = player.m_284548_(); // serverLevel()
        if (!IGRIS_DIMENSION.equals(level.m_46472_().m_135782_().toString())) { // dimension().location()
            return;
        }
        final SololevelingModVariables.PlayerVariables vars = ((ICapabilityProvider) player)
                .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                .orElse(null);
        if (vars == null || vars.instancecomplete || vars.jobtimer <= FINISH_VALUE) {
            return;
        }

        final AABB area = player.m_20191_().m_82400_(RANGE); // getBoundingBox().inflate()

        final List<SpawnerPortalEntity> portals = level.m_6443_(SpawnerPortalEntity.class, area, e -> true);
        for (final SpawnerPortalEntity portal : portals) {
            final BlockPos at = portal.m_20183_(); // blockPosition()
            for (int i = 0; i < KNIGHTS_PER_PORTAL; i++) {
                final Entity knight = randomKnight(level.f_46441_.m_188503_(3)) // random.nextInt
                        .m_262496_(level, at, MobSpawnType.MOB_SUMMONED); // spawn
                if (knight != null) {
                    knight.m_146922_(level.f_46441_.m_188501_() * 360.0F); // setYRot(random.nextFloat)
                }
            }
            portal.m_146870_(); // discard
        }

        final List<Entity> knights = level.m_6443_(Entity.class, area, JobQuestWave::isKnight);
        if (!portals.isEmpty()) {
            for (final Entity knight : knights) {
                if (knight instanceof LivingEntity living) {
                    living.m_7292_(new MobEffectInstance(MobEffects.f_19619_, GLOW_TICKS, 0, false, false)); // GLOWING
                }
            }
            return;
        }

        if (vars.jobtimer < CLEAR_CHECK_BELOW && knights.isEmpty()) {
            vars.jobtimer = FINISH_VALUE;
            vars.syncPlayerVariables(player);
        }
    }

    private static EntityType<? extends Entity> randomKnight(final int roll) {
        if (roll == 0) {
            return SololevelingModEntities.D_KNIGHT_1.get();
        }
        if (roll == 1) {
            return SololevelingModEntities.D_KNIGHT_2.get();
        }
        return SololevelingModEntities.D_KNIGHT_3.get();
    }

    private static boolean isKnight(final Entity e) {
        return e.m_6084_() // isAlive
                && (e instanceof DKnight1Entity || e instanceof DKnight2Entity || e instanceof DKnight3Entity);
    }
}
