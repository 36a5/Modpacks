package dev.alshabab.shababparty;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.solocraft.entity.SpawnerPortalEntity;
import net.solocraft.network.SololevelingModVariables;

/**
 * Ends the Job Change Quest as soon as Igris dies.
 *
 * In Solo Leveling, killing Igris starts a 100 second survival (PlayerVariables.jobtimer = 100).
 * Spawner portals appear on the dungeon's unbreakable deepslate and drop Demon Knights until it
 * runs out. Killing knights does not shorten anything: the quest only ends when
 * JobAdvTimerProcedure counts jobtimer down to 1.
 *
 * Here, the first tick a player in the Igris dungeon has that timer running, the spawner portals
 * near them are removed and jobtimer is dropped to 2. JobAdvTimerProcedure takes it to 1 on its
 * next once-a-second step and finishes the quest through its own path (instance complete,
 * leftover dungeon mobs cleared, ending started), and JobChangeTalkMixin then skips the chat and
 * rolls the new job. So the job lands about a second after Igris falls, and nothing about the
 * ending is reimplemented here.
 *
 * Party runs: Igris's death starts the timer for every party member in the dungeon, and each of
 * them is finished the same way on their own tick.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JobQuestWave {

    private static final String IGRIS_DIMENSION = "sololeveling:dungeon_dimension_igris";
    private static final double RANGE = 150.0D;
    /** One second above the mod's finish value, so its own countdown step ends the quest. */
    private static final double FINISH_VALUE = 2.0D;

    private JobQuestWave() {
    }

    @SubscribeEvent
    public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
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
        for (final SpawnerPortalEntity portal : level.m_6443_(SpawnerPortalEntity.class, area, e -> true)) {
            portal.m_146870_(); // discard
        }
        vars.jobtimer = FINISH_VALUE;
        vars.syncPlayerVariables(player);
    }
}
