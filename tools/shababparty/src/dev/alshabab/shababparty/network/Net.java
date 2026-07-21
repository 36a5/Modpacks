package dev.alshabab.shababparty.network;

import dev.alshabab.shababparty.ShababParty;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's only network channel.
 *
 * <p>Both accepted-version predicates return true unconditionally, which makes the channel
 * optional: a server without shababparty never sends, a client without it never receives, and
 * neither side refuses the connection over a channel the other lacks.
 */
public final class Net {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ShababParty.MOD_ID, "main"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(v -> true)
            .serverAcceptedVersions(v -> true)
            .simpleChannel();

    private Net() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, DamageNumberPacket.class,
                DamageNumberPacket::encode,
                DamageNumberPacket::decode,
                DamageNumberPacket::handle);
        CHANNEL.registerMessage(1, TrackUpdatePacket.class,
                TrackUpdatePacket::encode,
                TrackUpdatePacket::decode,
                TrackUpdatePacket::handle);
    }

    public static void toPlayer(final ServerPlayer player, final DamageNumberPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void toPlayer(final ServerPlayer player, final TrackUpdatePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
