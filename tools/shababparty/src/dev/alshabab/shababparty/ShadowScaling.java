package dev.alshabab.shababparty;

import java.util.List;
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
 * Makes a Shadow Monarch's army a reflection of the Monarch.
 *
 * A shadow's max health and armour are a fraction of its OWNER's - the elites (Igris, Tusk, Beru) get
 * half, every other soldier a quarter - and its attack damage grows with the owner's Intelligence, so
 * a Monarch who pours his stat points into INT fields a genuinely dangerous army. Because the numbers
 * are read off the owner, the army scales with everything the owner does: levelling (Vitality raises
 * his max health), gearing up (armour), and redistributing stats.
 *
 * <h2>Which entities</h2>
 * The mod's own minecraft:shadows tag - exactly the 13 shadow soldiers. It excludes the mod's other
 * TamableAnimals (flame vortexes, bear traps, healing bells) that share the base class but are not
 * soldiers.
 *
 * <h2>Tracking the owner</h2>
 * Applied on EntityJoinLevelEvent for fresh summons, and re-evaluated on a timer across all loaded
 * shadows. The re-sync recomputes the targets from the owner's CURRENT stats and swaps the modifiers
 * only when a number actually changed, so the common case is a no-op. Fixed UUIDs mean there is never
 * more than one modifier per attribute from us, however often a shadow reloads.
 *
 * <h2>The floor</h2>
 * A shadow is never scaled below its vanilla stats. Targets are floored at the attribute's base
 * value, so a fresh 20 HP Monarch gets a normal Igris, not a 10 HP one - the fraction only raises.
 * A shadow with no resolvable player owner is left entirely alone.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShadowScaling {

    private static final TagKey<EntityType<?>> SHADOWS = TagKey.m_203882_(
            Registries.f_256939_, new ResourceLocation("minecraft", "shadows"));

    private static final UUID HEALTH_ID = UUID.fromString("2c9e0a41-7b6d-4f83-9a1e-6d0c5f3b82a7");
    private static final UUID ARMOR_ID = UUID.fromString("6a8f2e19-4d07-49c5-8b3a-e5d19c7f0b24");
    private static final UUID DAMAGE_ID = UUID.fromString("9f4b1d72-0e35-4c86-b7a9-1c8e4a2f5d90");
    private static final String HEALTH_NAME = "shababparty:shadow_health";
    private static final String ARMOR_NAME = "shababparty:shadow_armor";
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

    private static void scale(final TamableAnimal shadow) {
        if (!(shadow.m_269323_() instanceof Player owner)) { // getOwner
            return;
        }
        final SololevelingModVariables.PlayerVariables vars =
                ((ICapabilityProvider) owner)
                        .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                        .orElse(null);
        if (vars == null) {
            return;
        }

        final double fraction = isElite(shadow)
                ? ShababParty.Config.SHADOW_ELITE_FRACTION.get()
                : ShababParty.Config.SHADOW_STANDARD_FRACTION.get();

        // The owner's numbers, as they are right now. Max health includes everything Vitality has
        // given him; armour is the attribute value, so worn equipment counts.
        final double ownerHealth = owner.m_21233_(); // getMaxHealth
        final Attribute armorAttr = ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation("minecraft", "generic.armor"));
        final double ownerArmor = armorAttr == null ? 0.0D : owner.m_21133_(armorAttr); // getAttributeValue

        applyTarget(shadow, "generic.max_health", HEALTH_ID, HEALTH_NAME, ownerHealth * fraction, true);
        applyTarget(shadow, "generic.armor", ARMOR_ID, ARMOR_NAME, ownerArmor * fraction, false);

        // Damage rides Intelligence rather than the owner's own attack: a Monarch is a summoner, and
        // INT is the stat that should make the army hit harder. MULTIPLY_TOTAL amount = INT * perInt.
        applyMultiplier(shadow, "generic.attack_damage", DAMAGE_ID, DAMAGE_NAME,
                Math.max(0.0D, vars.Intelligence) * ShababParty.Config.SHADOW_DAMAGE_PER_INTELLIGENCE.get());
    }

    private static boolean isElite(final TamableAnimal shadow) {
        final ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(shadow.m_6095_());
        final List<? extends String> elites = ShababParty.Config.SHADOW_ELITES.get();
        return id != null && elites.contains(id.toString());
    }

    /**
     * Raise an attribute to an absolute target with a fixed-UUID ADDITION modifier, floored at the
     * attribute's base value - the fraction only ever raises a shadow, never weakens it. Replaced only
     * when the number actually changed; heals to the new full when max health grows.
     */
    private static void applyTarget(final LivingEntity entity, final String attributeId, final UUID id,
                                    final String name, final double rawTarget, final boolean isHealth) {
        final AttributeInstance instance = instanceOf(entity, attributeId);
        if (instance == null) {
            return;
        }
        final double base = instance.m_22115_(); // getBaseValue
        final double amount = Math.max(base, rawTarget) - base; // never below vanilla
        swapModifier(instance, id, name, amount, AttributeModifier.Operation.ADDITION);

        if (isHealth && entity.m_21223_() < entity.m_21233_() && amount > 0.0D) { // getHealth < getMaxHealth
            entity.m_21153_(entity.m_21233_()); // setHealth(getMaxHealth)
        }
    }

    private static void applyMultiplier(final LivingEntity entity, final String attributeId, final UUID id,
                                        final String name, final double amount) {
        final AttributeInstance instance = instanceOf(entity, attributeId);
        if (instance != null) {
            swapModifier(instance, id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL);
        }
    }

    private static AttributeInstance instanceOf(final LivingEntity entity, final String attributeId) {
        final Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation("minecraft", attributeId));
        return attribute == null ? null : entity.m_21051_(attribute); // getAttribute
    }

    /** Idempotent set: no-op when unchanged, otherwise remove-and-re-add under the same UUID. */
    private static void swapModifier(final AttributeInstance instance, final UUID id, final String name,
                                     final double amount, final AttributeModifier.Operation operation) {
        final AttributeModifier existing = instance.m_22111_(id); // getModifier
        if (existing != null && Math.abs(existing.m_22218_() - amount) < EPSILON) { // getAmount
            return;
        }
        if (existing != null) {
            instance.m_22120_(id); // removeModifier
        }
        instance.m_22125_(new AttributeModifier(id, name, amount, operation)); // addPermanentModifier
    }
}
