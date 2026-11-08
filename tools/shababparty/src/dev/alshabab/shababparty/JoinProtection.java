package dev.alshabab.shababparty;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps a player safe from the moment they join until they take control.
 *
 * <p>A player who joins is still looking at "Loading terrain" for several seconds, sometimes a
 * minute on a slow computer, while the server already has them standing in the world. In that
 * time mobs can reach them and they cannot react. So the server puts every joining player in
 * spectator mode (mobs ignore spectators, nothing can hurt them, they do not fall) and holds them
 * there until one of these happens:
 * <ul>
 *   <li>their client reports a key held, the mouse moved, or a screen opened (the normal case,
 *       see {@link dev.alshabab.shababparty.client.JoinInput}),</li>
 *   <li>they type in chat, run a command, open a container or interact with anything,</li>
 *   <li>their position or view changes on the server (a client without this mod),</li>
 *   <li>or the timeout passes.</li>
 * </ul>
 * Falling does not count: spectators do not fall, and the release happens only on input.
 *
 * <p>Auth puts players who still have to log in into spectator mode too, and sets survival when
 * they log in. While protected, this re-applies spectator every tick, so the player stays safe
 * through Auth's login. On release, the player gets the game mode they had when they joined
 * (survival if that was spectator) and is moved back to the exact spot they joined at, in case
 * the first key press moved the spectator camera. If Auth has not logged them in yet, Auth keeps
 * control of their game mode and the release only stops the protection.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JoinProtection {

    /** Ticks after joining before server-side position or rotation changes count as input. */
    private static final int SETTLE_TICKS = 40;

    private static final class State {
        final GameType previous;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;
        int ticks;

        State(final GameType previous, final ServerPlayer player) {
            this.previous = previous;
            this.x = player.m_20185_();
            this.y = player.m_20186_();
            this.z = player.m_20189_();
            this.yaw = player.m_146908_();
            this.pitch = player.m_146909_();
        }
    }

    private static final Map<UUID, State> PROTECTED = new HashMap<>();

    private JoinProtection() {
    }

    @SubscribeEvent
    public static void onLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!ShababParty.Config.JOIN_PROTECTION_ENABLED.get() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        GameType previous = player.f_8941_.m_9290_(); // gameMode.getGameModeForPlayer
        if (previous == GameType.SPECTATOR) {
            previous = GameType.SURVIVAL;
        }
        PROTECTED.put(player.m_20148_(), new State(previous, player));
        player.m_143403_(GameType.SPECTATOR); // setGameMode
    }

    @SubscribeEvent
    public static void onLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        PROTECTED.remove(event.getEntity().m_20148_());
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PROTECTED.isEmpty()) {
            return;
        }
        final MinecraftServer server = event.getServer();
        final int timeoutTicks = ShababParty.Config.JOIN_PROTECTION_TIMEOUT_SECONDS.get() * 20;
        final Iterator<Map.Entry<UUID, State>> it = PROTECTED.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, State> e = it.next();
            final State s = e.getValue();
            final ServerPlayer player = server.m_6846_().m_11259_(e.getKey()); // getPlayerList().getPlayer
            if (player == null) {
                it.remove();
                continue;
            }
            s.ticks++;
            if (s.ticks >= timeoutTicks) {
                it.remove();
                finish(player, s, "timeout");
                continue;
            }
            if (s.ticks > SETTLE_TICKS && movedOnServer(player, s)) {
                it.remove();
                finish(player, s, "movement");
                continue;
            }
            if (player.f_8941_.m_9290_() != GameType.SPECTATOR) {
                player.m_143403_(GameType.SPECTATOR); // Auth's login just set survival; stay safe until input
            }
            if (s.ticks % 20 == 0 && isLoggedIn(player)) {
                runAs(server, player, "title @s actionbar {\"text\":\"You are safe until you press a key or move the mouse.\",\"color\":\"gray\"}");
            }
        }
    }

    /** Called from {@link dev.alshabab.shababparty.network.ReadyPacket} on the server thread. */
    public static void onReady(final ServerPlayer player) {
        release(player, "input");
    }

    @SubscribeEvent
    public static void onChat(final ServerChatEvent event) {
        release(event.getPlayer(), "chat");
    }

    @SubscribeEvent
    public static void onCommand(final CommandEvent event) {
        final Entity source = event.getParseResults().getContext().getSource().m_81373_(); // getEntity
        if (source instanceof ServerPlayer player) {
            release(player, "command");
        }
    }

    @SubscribeEvent
    public static void onInteract(final PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player, "interaction");
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(final PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player, "container");
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player, "dimension change");
        }
    }

    /** Belt and braces for the single tick between Auth setting survival and the next tick here. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttacked(final LivingAttackEvent event) {
        if (PROTECTED.containsKey(event.getEntity().m_20148_())) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- internals

    private static void release(final ServerPlayer player, final String reason) {
        final State s = PROTECTED.remove(player.m_20148_());
        if (s != null) {
            finish(player, s, reason);
        }
    }

    private static void finish(final ServerPlayer player, final State s, final String reason) {
        final MinecraftServer server = player.m_20194_(); // getServer
        if (server == null) {
            return;
        }
        if (isLoggedIn(player)) {
            if (Math.abs(player.m_20185_() - s.x) > 0.01 || Math.abs(player.m_20186_() - s.y) > 0.01
                    || Math.abs(player.m_20189_() - s.z) > 0.01) {
                player.m_6021_(s.x, s.y, s.z); // teleportTo: undo any spectator drift from the first key press
            }
            player.m_143403_(s.previous);
            runAs(server, player, "title @s actionbar \"\"");
        }
        ShababParty.LOGGER.debug("Join protection ended for {} ({}) after {} ticks", player.m_6302_(), reason, s.ticks);
    }

    private static boolean movedOnServer(final ServerPlayer player, final State s) {
        return Math.abs(player.m_20185_() - s.x) > 0.05 || Math.abs(player.m_20186_() - s.y) > 0.05
                || Math.abs(player.m_20189_() - s.z) > 0.05
                || Math.abs(player.m_146908_() - s.yaw) > 1.0f || Math.abs(player.m_146909_() - s.pitch) > 1.0f;
    }

    /** Auth has let this player in (password, device, or bypass), so game mode is ours to set. */
    private static boolean isLoggedIn(final ServerPlayer player) {
        final Set<String> tags = player.m_19880_(); // getTags
        return tags.contains("auth.logged") || tags.contains("auth.bypass.logged");
    }

    private static void runAs(final MinecraftServer server, final ServerPlayer player, final String command) {
        server.m_129892_().m_230957_(player.m_20203_().m_81324_().m_81325_(2), command);
    }
}
