package dev.alshabab.shababparty;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.solocraft.network.SololevelingModVariables;

/**
 * Killing a scaled boss grants Solo Leveling levels to the whole party in range - the way to level up
 * by fighting bosses. The count ramps up the progression: the third number in each bossTiers entry, or
 * bossBaseLevels for an unlisted boss.
 *
 * <h2>How a level is granted</h2>
 * Solo Leveling's level-up is LevelUpProcedure.onPlayerTick: each tick, while Xp >= MaxXP, it spends
 * one MaxXP, does Level++, and grants that level's stat points and rank. We do not touch Level
 * directly - that would skip the stats and leave a hollow level. Instead we add (levels * MaxXP) to
 * Xp and let the mod's own tick consume it into that many correct level-ups. It lands approximately N
 * levels (exact if MaxXP were constant; it drifts a little as the threshold grows, which for a reward
 * is immaterial), and it cannot desync the mod's derived values because the mod does the level-ups.
 *
 * <h2>Who gets paid</h2>
 * Every party member within [party] xpShareRadius of the kill, in the same dimension, INCLUDING the
 * killer - reusing {@link PartySupport#partyRecipients}. This is on top of Solo Leveling's own small
 * per-kill XP, which {@link PartySupport} already shares.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossLevels {

    private BossLevels() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBossDeath(final LivingDeathEvent event) {
        if (!ShababParty.Config.BOSS_LEVELS_ENABLED.get()) {
            return;
        }
        final LivingEntity dead = event.getEntity();
        final Level level = dead.m_9236_();
        if (level.m_5776_() || !BossScaling.isScaled(dead)) {
            return;
        }

        final Player killer = PartySupport.resolveXpEarner(event.getSource());
        if (killer == null) {
            return;
        }

        final BossScaling.Tier tier = BossScaling.tierFor(dead);
        final int levels = tier != null ? tier.levels() : ShababParty.Config.BOSS_BASE_LEVELS.get();
        if (levels <= 0) {
            return;
        }

        for (final ServerPlayer member : PartySupport.partyRecipients(killer, dead, level)) {
            grantLevels(member, levels);
        }
    }

    private static void grantLevels(final ServerPlayer player, final int levels) {
        final SololevelingModVariables.PlayerVariables vars =
                ((ICapabilityProvider) player)
                        .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                        .orElse(null);
        if (vars == null || vars.MaxXP <= 0.0D) {
            return; // not awakened as a hunter - nothing to level
        }
        vars.Xp += levels * vars.MaxXP;
        vars.syncPlayerVariables(player);
    }
}
