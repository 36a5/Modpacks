package dev.alshabab.shababparty.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One damage event, addressed to the one player who should see it.
 *
 * <p>The bucket is decided server-side because only the server knows both attacker and victim. A
 * player-versus-player hit produces two of these -- OUTGOING to the attacker, PLAYER_TO_YOU to the
 * victim -- so each sees their own side of it.
 */
public final class DamageNumberPacket {

    public static final int OUTGOING = 0;
    public static final int MOB_TO_YOU = 1;
    public static final int PLAYER_TO_YOU = 2;
    /** Damage another player dealt to a mob near you -- so a party can read each other's hits. */
    public static final int ALLY_TO_MOB = 3;

    public final int entityId;
    public final float raw;
    public final float finalAmount;
    public final int bucket;

    public DamageNumberPacket(int entityId, float raw, float finalAmount, int bucket) {
        this.entityId = entityId;
        this.raw = raw;
        this.finalAmount = finalAmount;
        this.bucket = bucket;
    }

    public static void encode(final DamageNumberPacket p, final FriendlyByteBuf buf) {
        buf.m_130130_(p.entityId);
        buf.writeFloat(p.raw);
        buf.writeFloat(p.finalAmount);
        buf.m_130130_(p.bucket);
    }

    public static DamageNumberPacket decode(final FriendlyByteBuf buf) {
        return new DamageNumberPacket(
                buf.m_130242_(), buf.readFloat(), buf.readFloat(), buf.m_130242_());
    }

    /**
     * The doubly-nested supplier in unsafeRunWhenOn is not a style flourish. It keeps the reference
     * to ClientDamageNumbers inside a lambda body that is only ever instantiated on the client, so a
     * dedicated server never classloads a net.minecraft.client type and never crashes on boot.
     */
    public static void handle(final DamageNumberPacket p, final Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.alshabab.shababparty.client.ClientDamageNumbers.accept(p)));
        ctx.get().setPacketHandled(true);
    }
}
