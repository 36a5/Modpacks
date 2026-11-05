package dev.alshabab.shababparty.network;

import java.util.function.Supplier;

import dev.alshabab.shababparty.DeviceLogin;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server, once per join: this computer's device key. See {@link DeviceLogin}.
 */
public final class DeviceHelloPacket {

    /** 32 random bytes as hex. Anything longer is not ours and is cut off by the read limit. */
    private static final int MAX_LENGTH = 128;

    public final String key;

    public DeviceHelloPacket(final String key) {
        this.key = key;
    }

    public static void encode(final DeviceHelloPacket p, final FriendlyByteBuf buf) {
        buf.m_130072_(p.key, MAX_LENGTH); // writeUtf(String, int)
    }

    public static DeviceHelloPacket decode(final FriendlyByteBuf buf) {
        return new DeviceHelloPacket(buf.m_130136_(MAX_LENGTH)); // readUtf(int)
    }

    public static void handle(final DeviceHelloPacket p, final Supplier<NetworkEvent.Context> ctx) {
        final ServerPlayer sender = ctx.get().getSender();
        if (sender != null) { // null when it arrives on a client, which never happens on purpose
            ctx.get().enqueueWork(() -> DeviceLogin.onHello(sender, p.key));
        }
        ctx.get().setPacketHandled(true);
    }
}
