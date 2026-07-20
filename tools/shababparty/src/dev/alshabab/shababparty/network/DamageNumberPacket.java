package dev.alshabab.shababparty.network;

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
}
