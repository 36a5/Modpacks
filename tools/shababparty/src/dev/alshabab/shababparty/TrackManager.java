package dev.alshabab.shababparty;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.alshabab.shababparty.network.Net;
import dev.alshabab.shababparty.network.TrackUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Server-side registry of who is tracking whom, and the tick loop that feeds each tracker its
 * target's position.
 *
 * <p>The whole point is that the target never learns of it. Nothing here ever touches the tracked
 * player: no message, no effect, no packet to their client. Only the tracker receives
 * {@link TrackUpdatePacket}s, and only ever about someone else.
 *
 * <p>State is in-memory. Tracking survives the tracker relogging (the map is keyed by the tracker's
 * UUID, so it resumes the moment they reconnect) but not a server restart -- a deliberately light
 * footprint for what is an operator convenience, not persisted data.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackManager {

    /** tracker UUID -> target UUID. */
    private static final Map<UUID, UUID> TRACKING = new ConcurrentHashMap<>();

    /** How often, in server ticks, each tracker is sent a fresh position. 4 = five times a second. */
    private static final int UPDATE_INTERVAL = 4;

    private static int tickCounter;

    private TrackManager() {
    }

    /** Start (or redirect) an operator's tracking. Returns silently; the command does the messaging. */
    public static void start(final UUID tracker, final UUID target) {
        TRACKING.put(tracker, target);
    }

    /** Stop tracking. Returns true if something was actually being tracked. */
    public static boolean stop(final UUID tracker) {
        return TRACKING.remove(tracker) != null;
    }

    /** The target an operator is currently tracking, or null. */
    public static UUID targetOf(final UUID tracker) {
        return TRACKING.get(tracker);
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRACKING.isEmpty()) {
            return;
        }
        if (++tickCounter % UPDATE_INTERVAL != 0) {
            return;
        }

        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        final Iterator<Map.Entry<UUID, UUID>> it = TRACKING.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, UUID> e = it.next();

            final ServerPlayer tracker = server.m_6846_().m_11259_(e.getKey());
            if (tracker == null) {
                // Tracker is offline. Keep the entry so it resumes on reconnect; nothing to send now.
                continue;
            }

            final ServerPlayer target = server.m_6846_().m_11259_(e.getValue());
            if (target == null) {
                Net.toPlayer(tracker, new TrackUpdatePacket(
                        TrackUpdatePacket.STATE_OFFLINE, "", "", 0, 0, 0, -1));
                continue;
            }

            final String dim = target.m_9236_().m_46472_().m_135782_().toString();
            Net.toPlayer(tracker, new TrackUpdatePacket(
                    TrackUpdatePacket.STATE_ACTIVE,
                    target.m_6302_(),               // getScoreboardName
                    dim,
                    target.m_20185_(), target.m_20186_(), target.m_20189_(),
                    target.m_19879_()));            // getId
        }
    }
}
