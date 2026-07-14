package dev.alshabab.shababparty;

import java.util.List;
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
 * Makes the pack's bosses a fight again.
 *
 * Solo Leveling's levelling curve leaves players strong enough that the rest of the pack's bosses die
 * in seconds. Everything in the shababparty:scaled_bosses tag gets its max health and its outgoing
 * damage multiplied. Solo Leveling's own bosses are untouched: they live in the mod's own
 * minecraft:soloboss tag, never reach ours, and are excluded by namespace as well.
 *
 * <h2>Health: one permanent modifier, one time</h2>
 * The multiplier is an AttributeModifier on MAX_HEALTH with a *fixed* UUID.
 *
 * That matters more than it looks. Attribute modifiers are serialised into the mob's NBT, and
 * EntityJoinLevelEvent fires again every time its chunk reloads. Re-applying blindly would multiply a
 * boss by 30 on every chunk load until its health overflowed - walk away and come back three times and
 * the Hydra has 97 million HP. Keying the modifier to a constant UUID and checking for it first makes
 * the operation idempotent: a boss is scaled exactly once, ever, however often it is loaded.
 *
 * Health is topped up to the new maximum only on that first application, so returning to a boss you
 * already wounded does not heal it.
 *
 * <h2>Damage: the hurt event, not the attribute</h2>
 * Scaling the ATTACK_DAMAGE attribute would do *nothing at all* to the Ender Dragon, which does not
 * read it - its damage is hardcoded in its attack phases. Most of Cataclysm's bosses are the same:
 * their real damage arrives as projectiles and area attacks carrying their own configured values
 * (Voidrunedamage, AbyssBlastdamage, DeathLaserdamage). The attribute would look scaled and the fight
 * would feel identical.
 *
 * So damage is multiplied where it actually lands. LivingHurtEvent fires for every source - melee,
 * projectile, spell, breath, explosion - and DamageSource.getEntity() names the *owner*, so a dragon's
 * fireball is credited to the dragon rather than to the fireball.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossScaling {

    /** The bosses we scale. Shipped in this jar; a datapack may extend it. */
    private static final TagKey<EntityType<?>> SCALED_BOSSES = TagKey.m_203882_(
            Registries.f_256939_, new ResourceLocation(ShababParty.MOD_ID, "scaled_bosses"));

    private static final ResourceLocation ENDER_DRAGON = new ResourceLocation("minecraft", "ender_dragon");

    /**
     * Constant, so the modifier can be recognised on a boss that has already been scaled and loaded
     * back off disk. Randomising it would re-scale every boss on every chunk load.
     */
    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("5b8a1f6c-3d21-4f7e-9c14-8e2a7d0b46f3");

    private static final String HEALTH_MODIFIER_NAME = "shababparty:boss_health";

    private BossScaling() {
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
            // Already scaled - loaded off disk with the modifier still in its NBT. Leave it alone.
            return;
        }

        final double multiplier = isEnderDragon(boss)
                ? ShababParty.Config.DRAGON_HEALTH_MULTIPLIER.get()
                : ShababParty.Config.BOSS_HEALTH_MULTIPLIER.get();

        // MULTIPLY_TOTAL: final = base * (1 + amount), so 30x is an amount of 29.
        attribute.m_22125_(new AttributeModifier(
                HEALTH_MODIFIER_ID, HEALTH_MODIFIER_NAME, multiplier - 1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL));

        // Only on first application. A boss loaded back at half health stays at half health.
        boss.m_21153_(boss.m_21233_());
    }

    @SubscribeEvent
    public static void onHurt(final LivingHurtEvent event) {
        if (!ShababParty.Config.BOSS_SCALING_ENABLED.get()) {
            return;
        }
        final DamageSource source = event.getSource();

        // The owner, not the projectile: a dragon's fireball is the dragon's damage.
        final Entity attacker = source.m_7639_();
        if (!(attacker instanceof LivingEntity boss) || !isScaled(boss)) {
            return;
        }

        final double multiplier = isEnderDragon(boss)
                ? ShababParty.Config.DRAGON_DAMAGE_MULTIPLIER.get()
                : ShababParty.Config.BOSS_DAMAGE_MULTIPLIER.get();

        event.setAmount((float) (event.getAmount() * multiplier));
    }

    /**
     * In the tag, and not excluded.
     *
     * Public because {@link BossLoot} keys off the same answer: a boss whose health and damage we
     * left alone - the Naga, the Lich, anything of Solo Leveling's - keeps its vanilla loot too. One
     * definition of "a boss we touched", so the two features cannot drift apart.
     */
    public static boolean isScaled(final LivingEntity entity) {
        return (entity.m_6095_().m_204039_(SCALED_BOSSES) || isEnderDragon(entity))
                && !isExcluded(entity);
    }

    private static boolean isEnderDragon(final LivingEntity entity) {
        return ENDER_DRAGON.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.m_6095_()));
    }

    /**
     * An exclusion is either a full id ("twilightforest:naga") or a bare namespace ("sololeveling"),
     * which excludes everything that mod registers.
     */
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
