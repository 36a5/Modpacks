package dev.alshabab.shababparty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Turns the pack's bosses into a difficulty curve, so a player has to upgrade and level up to move
 * from one dimension to the next.
 *
 * <h2>Why targets, not multipliers</h2>
 * Base health does not track difficulty. Twilight Forest's Knight Phantom is the fifth boss in the
 * gated ladder yet has 35 base HP - a tenth of the Hydra before it - so any multiplier, flat or
 * ramping, makes the later boss weaker. Cataclysm's hardest boss has less base HP than one of its
 * early ones. So each laddered boss is given the health it should END at, in progression order, from
 * the bossTiers config. A boss with no tier entry falls back to the flat healthMultiplier (the
 * overworld floor). The Ender Dragon is just the top tier entry.
 *
 * <h2>Membership</h2>
 * The shababparty:scaled_bosses tag, minus the exclusions list (Solo Leveling's own bosses). Solo
 * Leveling keeps its bosses in its own minecraft:soloboss tag, so they never reach ours anyway; the
 * namespace exclusion is belt and braces.
 *
 * <h2>Health: one ADDITION modifier, one time</h2>
 * A target is hit with a fixed-UUID Operation.ADDITION modifier of (target - baseValue). ADDITION off
 * the base value lands an absolute number; the pack leaves every mod's own health-multiplier config at
 * 1.0 so nothing multiplies it away. The fixed UUID makes it idempotent: attribute modifiers persist
 * in NBT and EntityJoinLevelEvent fires on every chunk load, so without the getModifier check a boss
 * would be re-scaled every time its chunk loaded. Health is topped up only on first application, so a
 * reloaded wounded boss stays wounded.
 *
 * <h2>Damage: the hurt event</h2>
 * Scaling ATTACK_DAMAGE would do nothing to the Ender Dragon (it does not read it) or to most
 * Cataclysm bosses (projectiles with their own numbers). So damage is multiplied on LivingHurtEvent,
 * credited to the owner - a dragon's fireball is the dragon's damage.
 *
 * AttributeFix is a hard dependency: vanilla caps max_health at 1024 and clamps anything above it, so
 * without the cap lifted every target here collapses to 1024.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossScaling {

    /** One boss's tuning: absolute final health, outgoing-damage multiplier, Solo Leveling levels per kill. */
    record Tier(double health, double damage, int levels) {
    }

    private static final TagKey<EntityType<?>> SCALED_BOSSES = TagKey.m_203882_(
            Registries.f_256939_, new ResourceLocation(ShababParty.MOD_ID, "scaled_bosses"));

    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("5b8a1f6c-3d21-4f7e-9c14-8e2a7d0b46f3");
    private static final String HEALTH_MODIFIER_NAME = "shababparty:boss_health";

    /** Parsed bossTiers, built once on first use. */
    private static Map<ResourceLocation, Tier> tiers;

    private BossScaling() {
    }

    /** "entity_id=health,damage,levels" lines -> map. Malformed lines are skipped rather than fatal. */
    private static Map<ResourceLocation, Tier> tiers() {
        if (tiers == null) {
            final Map<ResourceLocation, Tier> parsed = new HashMap<>();
            for (final String entry : ShababParty.Config.BOSS_TIERS.get()) {
                try {
                    final int eq = entry.indexOf('=');
                    final ResourceLocation id = new ResourceLocation(entry.substring(0, eq).trim());
                    final String[] parts = entry.substring(eq + 1).split(",");
                    parsed.put(id, new Tier(
                            Double.parseDouble(parts[0].trim()),
                            Double.parseDouble(parts[1].trim()),
                            Integer.parseInt(parts[2].trim())));
                } catch (final RuntimeException ignored) {
                    // A typo'd config line must not take the whole feature down with it.
                }
            }
            tiers = parsed;
        }
        return tiers;
    }

    /** The tier for this entity, or null if it has no explicit entry (falls back to the flat numbers). */
    static Tier tierFor(final LivingEntity entity) {
        final ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.m_6095_());
        return id == null ? null : tiers().get(id);
    }

    @SubscribeEvent
    public static void onJoin(final EntityJoinLevelEvent event) {
        if (event.getLevel().m_5776_() || !ShababParty.Config.BOSS_SCALING_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity boss) || !isScaled(boss)) {
            return;
        }

        // MAX_HEALTH via the registry rather than the SRG-mangled Attributes field: same reason, and
        // the same workaround, as UltraInstinct.playAt uses for sounds.
        final Attribute maxHealth = ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation("minecraft", "generic.max_health"));
        if (maxHealth == null) {
            return;
        }
        final AttributeInstance attribute = boss.m_21051_(maxHealth);
        if (attribute == null || attribute.m_22111_(HEALTH_MODIFIER_ID) != null) {
            return; // already scaled - loaded off disk with the modifier still in its NBT
        }

        final double baseValue = attribute.m_22115_(); // getBaseValue
        final Tier tier = tierFor(boss);
        final double target = tier != null
                ? tier.health()
                : baseValue * ShababParty.Config.BOSS_HEALTH_MULTIPLIER.get();

        // ADDITION: final = base + amount, so the boss lands exactly on target.
        attribute.m_22125_(new AttributeModifier(
                HEALTH_MODIFIER_ID, HEALTH_MODIFIER_NAME, target - baseValue,
                AttributeModifier.Operation.ADDITION));

        boss.m_21153_(boss.m_21233_()); // setHealth(getMaxHealth) - first application only
    }

    @SubscribeEvent
    public static void onHurt(final LivingHurtEvent event) {
        if (!ShababParty.Config.BOSS_SCALING_ENABLED.get()) {
            return;
        }
        final DamageSource source = event.getSource();
        final Entity attacker = source.m_7639_(); // owner, not the projectile
        if (!(attacker instanceof LivingEntity boss) || !isScaled(boss)) {
            return;
        }

        final Tier tier = tierFor(boss);
        final double multiplier = tier != null
                ? tier.damage()
                : ShababParty.Config.BOSS_DAMAGE_MULTIPLIER.get();

        event.setAmount((float) (event.getAmount() * multiplier));
    }

    /**
     * In the tag, and not excluded. Public because {@link BossLevels} keys off the same answer: a boss
     * we did not scale grants no bonus levels either.
     */
    public static boolean isScaled(final LivingEntity entity) {
        return entity.m_6095_().m_204039_(SCALED_BOSSES) && !isExcluded(entity);
    }

    private static boolean isExcluded(final LivingEntity entity) {
        final ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.m_6095_());
        if (id == null) {
            return false;
        }
        final List<? extends String> exclusions = ShababParty.Config.BOSS_SCALING_EXCLUSIONS.get();
        // m_135827_ = getNamespace.
        return exclusions.contains(id.toString()) || exclusions.contains(id.m_135827_());
    }
}
