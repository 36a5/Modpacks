package dev.alshabab.shababparty;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.registries.ForgeRegistries;
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

        // The reward is the boss's actual max health at death (m_21233_) times xpPerHp: a harder
        // boss always pays more, and retuning a boss with /scaling tier retunes its reward in the
        // same stroke. 5 per HP (default) -> the ~11k Yeti pays ~55k XP, the 150k Dragon 750k.
        final double maxHp = dead.m_21233_();
        final double baseXp = maxHp * ShababParty.Config.XP_PER_HP.get();
        if (baseXp <= 0.0D) {
            return;
        }

        // Deathless fights pay extra - read here, before BossLoot's drops handler forgets the boss.
        final boolean clean = BossFightTracker.isClean(dead);
        final double cleanMult = clean ? 1.0D + ShababParty.Config.NO_DEATH_BONUS.get() : 1.0D;

        // Endgame bosses also pay raw SkillPoints, so a max-level player still has a reason to be here.
        final double skillPoints = maxHp >= ShababParty.Config.SP_BOUNTY_THRESHOLD.get()
                ? maxHp / 10000.0D * ShababParty.Config.SP_PER_10K_HP.get() * cleanMult
                : 0.0D;

        final ResourceLocation bossId = ForgeRegistries.ENTITY_TYPES.getKey(dead.m_6095_());

        for (final ServerPlayer member : PartySupport.partyRecipients(killer, dead, level)) {
            double memberMult = cleanMult;
            if (bossId != null && isFirstKill(member, bossId)) {
                // The chase list: a player's first-ever kill of each boss type pays double.
                memberMult *= ShababParty.Config.MILESTONE_MULTIPLIER.get();
                member.m_5661_(Component.m_237113_("First " + bossId.m_135815_() + " kill - bonus reward!")
                        .m_130944_(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
            }
            if (clean) {
                member.m_5661_(Component.m_237113_("Deathless fight - +"
                                + (int) (ShababParty.Config.NO_DEATH_BONUS.get() * 100) + "% reward!")
                        .m_130944_(ChatFormatting.AQUA), false);
            }
            grant(member, baseXp * memberMult, skillPoints);
        }
    }

    /**
     * First-ever kill of this boss type for this player. Recorded under PlayerPersisted, the one
     * subtree of an entity's ForgeData that Forge copies across death, so dying later does not reset
     * the chase list.
     */
    private static boolean isFirstKill(final ServerPlayer player, final ResourceLocation bossId) {
        // getPersistentData is a Forge patch on Entity; reached via IForgeEntity for the same reason
        // getCapability goes through ICapabilityProvider (no patched jar on disk to compile against).
        final CompoundTag root = ((net.minecraftforge.common.extensions.IForgeEntity) player).getPersistentData();
        final CompoundTag persisted = root.m_128469_("PlayerPersisted"); // getCompound
        if (!root.m_128441_("PlayerPersisted")) { // contains - getCompound returns a detached tag when absent
            root.m_128365_("PlayerPersisted", persisted); // put
        }
        final CompoundTag kills = persisted.m_128469_("shababpartyBossKills");
        if (!persisted.m_128441_("shababpartyBossKills")) {
            persisted.m_128365_("shababpartyBossKills", kills);
        }
        final boolean first = !kills.m_128471_(bossId.toString()); // getBoolean
        kills.m_128379_(bossId.toString(), true); // putBoolean
        return first;
    }

    private static void grant(final ServerPlayer player, final double xp, final double skillPoints) {
        final SololevelingModVariables.PlayerVariables vars =
                ((ICapabilityProvider) player)
                        .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                        .orElse(null);
        if (vars == null || vars.MaxXP <= 0.0D) {
            return; // not awakened as a hunter - nothing to gain
        }
        // Straight into the XP pool. The mod's LevelUpProcedure.onPlayerTick spends it into as many
        // levels as it buys, each with its stat points, over the next ticks - so a 750k lump on the
        // Ender Dragon cascades through the levels on its own without us touching Level.
        vars.Xp += xp;
        vars.SkillPoints += skillPoints;
        vars.syncPlayerVariables(player);
    }
}
