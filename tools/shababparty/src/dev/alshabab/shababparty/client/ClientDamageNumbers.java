package dev.alshabab.shababparty.client;

import dev.alshabab.shababparty.ShababParty;
import dev.alshabab.shababparty.network.DamageNumberPacket;

/**
 * Temporary sink so the wire format can be verified before any rendering exists. Replaced by the
 * real popup store in the next commit.
 */
public final class ClientDamageNumbers {

    private ClientDamageNumbers() {
    }

    public static void accept(final DamageNumberPacket p) {
        ShababParty.LOGGER.info("damage number: bucket={} entity={} raw={} final={}",
                p.bucket, p.entityId, p.raw, p.finalAmount);
    }
}
