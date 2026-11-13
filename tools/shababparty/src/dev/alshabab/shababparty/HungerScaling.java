package dev.alshabab.shababparty;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import net.solocraft.network.SololevelingModVariables;

/**
 * Hunger drains slower the higher a player's Solo Leveling level. Called from
 * {@link dev.alshabab.shababparty.mixin.PlayerExhaustionMixin} with every bit of exhaustion the player
 * is about to take. Server side only: the server owns the food bar and syncs it to the client.
 */
public final class HungerScaling {

    private HungerScaling() {
    }

    public static float scale(final Player player, final float exhaustion) {
        if (exhaustion <= 0.0F || player.m_9236_().m_5776_() || !ShababParty.Config.HUNGER_SCALING_ENABLED.get()) {
            return exhaustion;
        }
        final SololevelingModVariables.PlayerVariables vars = ((ICapabilityProvider) player)
                .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                .orElse(null);
        if (vars == null) {
            return exhaustion;
        }
        return (float) (exhaustion * factor(vars.Level));
    }

    /** 1 / (1 + level * perLevel), floored at minFactor. */
    public static double factor(final double level) {
        final double perLevel = ShababParty.Config.HUNGER_PER_LEVEL.get();
        final double raw = 1.0D / (1.0D + Math.max(0.0D, level) * perLevel);
        return Math.max(ShababParty.Config.HUNGER_MIN_FACTOR.get(), Math.min(1.0D, raw));
    }
}
