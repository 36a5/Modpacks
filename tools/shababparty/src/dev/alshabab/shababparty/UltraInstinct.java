package dev.alshabab.shababparty;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import net.solocraft.entity.AfterImageEntity;
import net.solocraft.init.SololevelingModEntities;

/**
 * Monarch of White Flames' fourth ability: a counter stance.
 *
 * Press V and, for a few seconds, the next hits do not land. Each attacker is answered instead: the
 * player leaves an after-image where he was standing, appears behind whoever swung, and strikes them
 * with whatever he is holding.
 *
 * <h2>What "animation" means here</h2>
 * A new keyframed animation would need a model rig and a GeckoLib controller, which is not something
 * that can be added to a compiled mod from the outside. What exists instead is the mod's own
 * AfterImageEntity - built, registered, and (like the six unused flame abilities) never used by
 * anything. It is spawned at the position the player vanishes from, so the dodge reads as a
 * blink-and-afterimage rather than a silent teleport. The counter itself is a real melee swing, so
 * it plays the player's normal attack animation, damage, enchantments and knockback.
 *
 * <h2>Why LivingAttackEvent</h2>
 * It fires before damage is calculated and it is cancellable, so cancelling there means the hit
 * never happened - no armour damage, no hurt animation, no knockback. LivingHurtEvent would be too
 * late to dodge cleanly.
 *
 * The counter is a real Player.attack(), which itself fires a LivingAttackEvent on the target. The
 * COUNTERING flag stops that from re-entering this handler if the target somehow reflects damage.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UltraInstinct {

    /** Game tick at which each player's stance ends. */
    private static final Map<UUID, Long> ACTIVE_UNTIL = new HashMap<>();

    /** Game tick at which each player may press V again. */
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    /** Re-entrancy guard: our own counter-attack must not be treated as an incoming hit. */
    private static final ThreadLocal<Boolean> COUNTERING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * @return true if the stance was entered (so the "ability wip" message is swallowed). Still true
     *         when the ability is on cooldown: the player is told why, which is a better answer than
     *         "ability wip".
     */
    public static boolean activate(final ServerPlayer player) {
        final long now = player.m_9236_().m_46467_();
        final UUID id = player.m_20148_();

        final long readyAt = READY_AT.getOrDefault(id, 0L);
        if (now < readyAt) {
            final long secondsLeft = (readyAt - now + 19) / 20;
            player.m_5661_(Component.m_237113_("Ultra Instinct: " + secondsLeft + "s")
                    .m_130944_(ChatFormatting.DARK_GRAY), true);
            return true;
        }

        final int duration = ShababParty.Config.ULTRA_INSTINCT_DURATION_TICKS.get();
        ACTIVE_UNTIL.put(id, now + duration);
        READY_AT.put(id, now + duration + ShababParty.Config.ULTRA_INSTINCT_COOLDOWN_TICKS.get());

        // m_237113_ = Component.literal, m_130944_ = withStyle(ChatFormatting...): both SRG here.
        player.m_5661_(Component.m_237113_("Ultra Instinct").m_130944_(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        playAt(player, "block.beacon.activate", 1.0F, 1.4F);
        return true;
    }

    private static boolean isActive(final ServerPlayer player) {
        final Long until = ACTIVE_UNTIL.get(player.m_20148_());
        return until != null && player.m_9236_().m_46467_() < until;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingHit(final LivingAttackEvent event) {
        if (COUNTERING.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !isActive(player)) {
            return;
        }

        // The attacker, not the projectile: an arrow's owner is who we go after.
        final Entity source = event.getSource().m_7639_();
        if (!(source instanceof LivingEntity attacker) || attacker == player) {
            return;
        }

        if (ShababParty.Config.ULTRA_INSTINCT_DODGES.get()) {
            event.setCanceled(true);
        }
        counter(player, attacker);
    }

    private static void counter(final ServerPlayer player, final LivingEntity attacker) {
        final ServerLevel level = player.m_284548_();
        final Vec3 from = player.m_20182_();

        leaveAfterImage(level, player, from);

        // Behind the attacker means behind where *he* is facing, not where the player came from -
        // otherwise a player already standing behind him would not move at all.
        final Vec3 look = attacker.m_20154_();
        final Vec3 at = attacker.m_20182_();
        final double distance = ShababParty.Config.ULTRA_INSTINCT_BEHIND_DISTANCE.get();
        final double x = at.m_7096_() - look.m_7096_() * distance;
        final double z = at.m_7094_() - look.m_7094_() * distance;

        // Face the attacker from the new position.
        final float yaw = (float) (Math.toDegrees(Math.atan2(at.m_7094_() - z, at.m_7096_() - x)) - 90.0D);
        player.m_8999_(level, x, at.m_7098_(), z, yaw, 0.0F);

        playAt(player, "entity.enderman.teleport", 1.0F, 1.6F);

        COUNTERING.set(Boolean.TRUE);
        try {
            // A real melee swing: the held weapon's damage, enchantments, crit and knockback all
            // apply, and it plays the normal attack animation. Nothing to reimplement.
            player.m_5706_(attacker);
        } finally {
            COUNTERING.set(Boolean.FALSE);
        }
    }

    /** The mod's own after-image, spawned where the player was. Cosmetic: failure is not fatal. */
    private static void leaveAfterImage(final ServerLevel level, final ServerPlayer player, final Vec3 at) {
        try {
            final EntityType<AfterImageEntity> type = SololevelingModEntities.AFTER_IMAGE.get();
            final AfterImageEntity image = type.m_20615_(level);
            if (image != null) {
                image.m_7678_(at.m_7096_(), at.m_7098_(), at.m_7094_(), player.m_146908_(), 0.0F);
                level.m_7967_(image);
            }
        } catch (final Throwable ignored) {
            // A missing or changed after-image entity must not cost the player his counter.
        }
    }

    /**
     * Sounds are looked up by registry id rather than through SoundEvents, whose 1468 fields are all
     * SRG-mangled here (f_215670_ and friends). Picking one of those by hand is a guess that would
     * fail silently as the wrong noise.
     */
    private static void playAt(final ServerPlayer player, final String soundId,
                               final float volume, final float pitch) {
        final SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
        if (sound == null) {
            return;
        }
        final Vec3 at = player.m_20182_();
        player.m_9236_().m_6263_(null, at.m_7096_(), at.m_7098_(), at.m_7094_(),
                sound, SoundSource.PLAYERS, volume, pitch);
    }

    private UltraInstinct() {
    }
}
