package dev.alshabab.shababparty.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One position update for the player an operator is tracking, addressed only to that operator.
 *
 * <p>Covert by construction: the packet is only ever sent to the tracker, never to the target, and
 * carries nothing the target's client would receive. The server decides who is tracking whom (see
 * {@link dev.alshabab.shababparty.TrackManager}); the tracked player is told nothing.
 *
 * <p>Position travels as raw coordinates rather than only an entity id, because the target is
 * frequently outside the tracker's render distance or in another dimension, where the tracker's
 * client has no entity to look up. The entity id is sent too, as a hint for anything that wants the
 * live entity when it happens to be loaded, but rendering never depends on it.
 */
public final class TrackUpdatePacket {

    /** Tracking ended -- drop the ESP box and the waypoint. */
    public static final int STATE_CLEARED = 0;
    /** Target is online; x/y/z and dimension are current. */
    public static final int STATE_ACTIVE = 1;
    /** Target is offline; the last known position is stale but kept for reference. */
    public static final int STATE_OFFLINE = 2;

    public final int state;
    public final String name;
    public final String dimension;
    public final double x;
    public final double y;
    public final double z;
    public final int entityId;

    public TrackUpdatePacket(final int state, final String name, final String dimension,
                             final double x, final double y, final double z, final int entityId) {
        this.state = state;
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.entityId = entityId;
    }

    public static void encode(final TrackUpdatePacket p, final FriendlyByteBuf buf) {
        buf.m_130130_(p.state);          // writeVarInt
        buf.m_130070_(p.name);           // writeUtf
        buf.m_130070_(p.dimension);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.m_130130_(p.entityId);
    }

    public static TrackUpdatePacket decode(final FriendlyByteBuf buf) {
        return new TrackUpdatePacket(
                buf.m_130242_(),         // readVarInt
                buf.m_130277_(),         // readUtf
                buf.m_130277_(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.m_130242_());
    }

    /**
     * The doubly-nested supplier keeps the reference to ClientTracker inside a lambda body that is
     * only ever instantiated on the client, so a dedicated server never classloads a
     * net.minecraft.client type and never crashes on boot. Same pattern as
     * {@link DamageNumberPacket#handle}.
     */
    public static void handle(final TrackUpdatePacket p, final Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.alshabab.shababparty.client.ClientTracker.accept(p)));
        ctx.get().setPacketHandled(true);
    }
}
