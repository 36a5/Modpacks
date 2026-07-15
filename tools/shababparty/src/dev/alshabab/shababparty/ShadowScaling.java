package dev.alshabab.shababparty;

import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import net.solocraft.network.SololevelingModVariables;

/**
 * Scales a Shadow Monarch's shadow soldiers with his Solo Leveling level, and keeps them scaled as he
 * levels.
 *
 * The mod's shadows are static - a level 200 Monarch's Igris is a level 40 Monarch's Igris. Here a
 * shadow's health and attack damage are multiplied by (1 + ownerLevel * perLevel), so a high-level
 * Monarch fields a genuinely stronger army.
 *
 * <h2>Which entities</h2>
 * The mod's own minecraft:shadows tag - exactly the 13 shadow soldiers. It excludes the mod's other
 * TamableAnimals (flame vortexes, bear traps, healing bells) that share the base class but are not
 * soldiers.
 *
 * <h2>Growing as the owner levels</h2>
 * Scaling is applied to a freshly summoned shadow on EntityJoinLevelEvent, and re-evaluated on a timer
 * (resyncIntervalTicks) across all loaded shadows. The re-sync reads the owner's CURRENT level and, if
 * it now implies a different multiplier than the shadow carries, swaps the modifier - so a standing
 * army gets stronger the moment its Monarch levels up, not only on his next summon. The modifiers use
 * fixed UUIDs so this never stacks: there is always exactly one health modifier and one damage
 * modifier from us on any shadow.
 *
 * A shadow with no resolvable player owner is left at vanilla stats until its owner is around again.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShadowScaling {

    private static final TagKey<EntityType<?>> SHADOWS = TagKey.m_203882_(
            Registries.f_256939_, new ResourceLocation("minecraft", "shadows"));

    private static final UUID HEALTH_ID = UUID.fromString("2c9e0a41-7b6d-4f83-9a1e-6d0c5f3b82a7");
    private static final UUID DAMAGE_ID = UUID.fromString("9f4b1d72-0e35-4c86-b7a9-1c8e4a2f5d90");
    private static final String HEALTH_NAME = "shababparty:shadow_health";
    private static final String DAMAGE_NAME = "shababparty:shadow_damage";
    private static final double EPSILON = 1.0E-6D;

    private static int ticks;

    private ShadowScaling() {
    }

    @SubscribeEvent
    public static void onJoin(final EntityJoinLevelEvent event) {
        if (event.getLevel().m_5776_() || !ShababParty.Config.SHADOW_SCALING_ENABLED.get()) {
            return;
        }
        if (event.getEntity() instanceof TamableAnimal shadow && isShadow(shadow)) {
            scale(shadow);
        }
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ShababParty.Config.SHADOW_SCALING_ENABLED.get()) {
            return;
        }
        if (++ticks < ShababParty.Config.SHADOW_RESYNC_TICKS.get()) {
            return;
        }
        ticks = 0;

        for (final ServerLevel level : event.getServer().m_129785_()) { // getAllLevels
            for (final Entity entity : level.m_8583_()) {                // getAllEntities
                if (entity instanceof TamableAnimal shadow && isShadow(shadow)) {
                    scale(shadow);
                }
            }
        }
    }

    private static boolean isShadow(final TamableAnimal entity) {
        return entity.m_6095_().m_204039_(SHADOWS);
    }

    /** Owner's Solo Leveling level, or -1 if the owner is not a resolvable player. */
    private static int ownerLevel(final TamableAnimal shadow) {
        if (!(shadow.m_269323_() instanceof Player owner)) { // getOwner
            return -1;
        }
        final SololevelingModVariables.PlayerVariables vars =
                ((ICapabilityProvider) owner)
                        .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                        .orElse(null);
        return vars == null ? -1 : (int) vars.Level;
    }

    private static void scale(final TamableAnimal shadow) {
        final int level = ownerLevel(shadow);
        if (level < 0) {
            return;
        }
        applyModifier(shadow, "generic.max_health", HEALTH_ID, HEALTH_NAME,
                level * ShababParty.Config.SHADOW_HEALTH_PER_LEVEL.get(), true);
        applyModifier(shadow, "generic.attack_damage", DAMAGE_ID, DAMAGE_NAME,
                level * ShababParty.Config.SHADOW_DAMAGE_PER_LEVEL.get(), false);
    }

    /**
     * Set a fixed-UUID MULTIPLY_TOTAL modifier to {@code amount} (multiplier = 1 + amount), replacing
     * any previous one from us only when the number actually changed - so the common no-op re-sync
     * does nothing. On a max-health increase the shadow is healed to the new full.
     */
    private static void applyModifier(final LivingEntity entity, final String attributeId, final UUID id,
                                      final String name, final double amount, final boolean isHealth) {
        final Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation("minecraft", attributeId));
        if (attribute == null) {
            return;
        }
        final AttributeInstance instance = entity.m_21051_(attribute); // getAttribute
        if (instance == null) {
            return; // this shadow does not have that attribute (e.g. no attack damage)
        }

        final AttributeModifier existing = instance.m_22111_(id); // getModifier
        if (existing != null && Math.abs(existing.m_22218_() - amount) < EPSILON) {
            return; // unchanged since last sync
        }
        if (existing != null) {
            instance.m_22120_(id); // removeModifier
        }
        instance.m_22125_(new AttributeModifier( // addPermanentModifier
                id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));

        if (isHealth) {
            entity.m_21153_(entity.m_21233_()); // setHealth(getMaxHealth)
        }
    }
}
