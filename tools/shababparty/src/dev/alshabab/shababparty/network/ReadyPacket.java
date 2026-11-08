package dev.alshabab.shababparty.network;

import java.util.function.Supplier;

import dev.alshabab.shababparty.JoinProtection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server, no payload: "the player has pressed a key or moved the mouse since joining".
 * Ends the join protection. See {@link JoinProtection}.
 */
public final class ReadyPacket {

    public ReadyPacket() {
    }

    public static void encode(final ReadyPacket msg, final FriendlyByteBuf buf) {
    }

    public static ReadyPacket decode(final FriendlyByteBuf buf) {
        return new ReadyPacket();
    }

    public static void handle(final ReadyPacket msg, final Supplier<NetworkEvent.Context> ctx) {
        final ServerPlayer sender = ctx.get().getSender();
        if (sender != null) {
            ctx.get().enqueueWork(() -> JoinProtection.onReady(sender));
        }
        ctx.get().setPacketHandled(true);
    }
}
