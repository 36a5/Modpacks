package dev.alshabab.shababparty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes a scaled boss's loot worth the fight it now is.
 *
 * A boss with 150x health that drops exactly what it always dropped is a worse deal than walking
 * past it. This buffs what the boss already gives rather than rewriting forty loot tables across a
 * dozen mods: whatever a boss drops, it drops more of, and any gear among it comes out stronger.
 *
 * Membership is {@link BossScaling#isScaled}, deliberately. A boss whose health we left alone - the
 * Naga, the Lich, anything of Solo Leveling's - keeps its vanilla loot too. One definition of "a boss
 * we touched" means the two features cannot drift apart into a boss that is harder but not richer,
 * or richer but not harder.
 *
 * <h2>Gear versus everything else</h2>
 * A drop is treated as gear if it has durability ({@code isDamageableItem}). That is a blunt test,
 * and it is the right one: it catches every weapon, tool and piece of armour any of these mods can
 * drop without needing to know a single item id, and it excludes the materials and trophies, which
 * want quantity rather than enchantments.
 *
 * Gear that is already enchanted has every level raised past the vanilla ceiling - a Power V bow off
 * the Hydra becomes Power VIII. Gear that drops clean is run through the enchanting routine at a
 * level the vanilla table cannot reach.
 *
 * <h2>Why extra item entities rather than a bigger stack</h2>
 * Multiplying a stack's count is the obvious move and it is wrong: a count of 320 on an item that
 * maxes at 64 is not a legal stack, and it misbehaves the moment anything tries to pick it up or
 * store it. The extra drops are added as further ItemEntities instead, which is what a bigger loot
 * roll would have produced anyway.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossLoot {

    private BossLoot() {
    }

    @SubscribeEvent
    public static void onDrops(final LivingDropsEvent event) {
        if (!ShababParty.Config.BOSS_LOOT_ENABLED.get()) {
            return;
        }
        final LivingEntity dead = event.getEntity();
        if (dead.m_9236_().m_5776_() || !BossScaling.isScaled(dead)) {
            return;
        }

        final Level level = dead.m_9236_();
        final RandomSource random = dead.m_217043_();
        final int copies = ShababParty.Config.BOSS_LOOT_DROP_MULTIPLIER.get();

        final List<ItemEntity> extra = new ArrayList<>();

        for (final ItemEntity drop : event.getDrops()) {
            final ItemStack stack = drop.m_32055_();
            if (stack.m_41619_()) {
                continue;
            }

            if (stack.m_41763_()) {
                // Gear: one of it, but a better one. Duplicating a boss weapon would cheapen it.
                drop.m_32045_(strengthen(stack, random));
            } else {
                // Materials, trophies, resources: more of it.
                for (int i = 1; i < copies; i++) {
                    extra.add(new ItemEntity(level, dead.m_20185_(), dead.m_20186_(), dead.m_20189_(),
                            stack.m_41777_()));
                }
            }
        }

        // The HP bounty: a flat payment for the fight's size, on top of whatever the boss dropped.
        // Computed from the boss's actual max health, so a harder boss always pays more and a
        // /scaling retune moves the payout with it. Added AFTER the multiplication loop above, so
        // the bounty itself is never multiplied.
        final double per10k = dead.m_21233_() / 10000.0D; // getMaxHealth

        // Deathless fights pay half again as much bounty. This is the LAST reader of the fight
        // tracker (drops fire after the death event), so it also forgets the boss afterwards.
        final double cleanMult = BossFightTracker.isClean(dead)
                ? 1.0D + ShababParty.Config.NO_DEATH_BONUS.get()
                : 1.0D;

        for (final String entry : ShababParty.Config.HP_BOUNTY_ITEMS.get()) {
            try {
                final int eq = entry.indexOf('=');
                final net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(new net.minecraft.resources.ResourceLocation(entry.substring(0, eq).trim()));
                if (item == null) {
                    continue;
                }
                int total = (int) Math.max(1.0D,
                        Math.floor(per10k * Integer.parseInt(entry.substring(eq + 1).trim()) * cleanMult));
                final int maxStack = new ItemStack(item).m_41741_(); // getMaxStackSize
                while (total > 0) {
                    final int n = Math.min(total, maxStack);
                    extra.add(new ItemEntity(level, dead.m_20185_(), dead.m_20186_(), dead.m_20189_(),
                            new ItemStack(item, n)));
                    total -= n;
                }
            } catch (final RuntimeException ignored) {
                // A typo'd bounty line must not take the drops down with it.
            }
        }

        BossFightTracker.forget(dead);

        event.getDrops().addAll(extra);
    }

    @SubscribeEvent
    public static void onExperience(final LivingExperienceDropEvent event) {
        if (!ShababParty.Config.BOSS_LOOT_ENABLED.get() || !BossScaling.isScaled(event.getEntity())) {
            return;
        }
        final int multiplier = ShababParty.Config.BOSS_LOOT_XP_MULTIPLIER.get();
        event.setDroppedExperience(event.getDroppedExperience() * multiplier);
    }

    /**
     * Already enchanted, so raise what is there; otherwise enchant it from scratch. Raising beats
     * re-rolling on gear a mod shipped with deliberate enchantments - a boss weapon meant to carry
     * Fire Aspect should come out with more Fire Aspect, not with a random substitute.
     */
    private static ItemStack strengthen(final ItemStack stack, final RandomSource random) {
        if (stack.m_41793_()) {
            final Map<Enchantment, Integer> existing = EnchantmentHelper.m_44831_(stack);
            final int bonus = ShababParty.Config.BOSS_LOOT_BONUS_ENCHANT_LEVELS.get();

            final Map<Enchantment, Integer> raised = new HashMap<>();
            existing.forEach((enchantment, lvl) -> raised.put(enchantment, lvl + bonus));

            // Past the vanilla maximum on purpose. Power VIII does not exist in a book and that is
            // the whole reason the drop is worth having.
            EnchantmentHelper.m_44865_(raised, stack);
            return stack;
        }

        // m_220292_ = EnchantmentHelper.enchantItem. It returns the stack rather than mutating in
        // place, so the result has to be used - assigning it back is not optional.
        return EnchantmentHelper.m_220292_(
                random, stack, ShababParty.Config.BOSS_LOOT_ENCHANT_LEVEL.get(), true);
    }
}
