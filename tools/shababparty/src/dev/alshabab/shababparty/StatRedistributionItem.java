package dev.alshabab.shababparty;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.registries.ForgeRegistries;

import net.solocraft.network.SololevelingModVariables;

/**
 * Stat Redistribution: right-click and every point ever put into the six Solo Leveling stats -
 * Vitality, Strength, Intelligence, Perception, Speed, Durability - returns to unspent SP, ready to
 * be re-allocated in the panel. Crafted from a Class Chooser ringed by eight ancient debris (recipe in
 * res/data/shababparty/recipes/).
 *
 * A respec, not a reset: Level, Xp, rank and job are untouched. Every stat goes back to 0 and the sum
 * lands in SkillPoints, which is the same currency the panel's + buttons spend
 * (IntelligenceIncreaseProcedure checks and decrements SkillPoints), so the mod's own UI takes it
 * from there. The mod's per-stat Update procedures recompute the derived values - max health from
 * Vitality, Mana from Intelligence - on their own tick, so nothing needs recomputing here.
 *
 * All six stats default to 0 on a fresh player (PlayerVariables' constructor), so "back to 0" is a
 * true baseline, and the refund includes the automatic per-level stat gains - deliberately: the point
 * of the item is that ALL points become spendable, so a player can go all-in on one stat.
 */
public final class StatRedistributionItem extends Item {

    public StatRedistributionItem() {
        super(new Item.Properties());
    }

    // m_7203_ = use(Level, Player, InteractionHand)
    @Override
    public InteractionResultHolder<ItemStack> m_7203_(final Level level, final Player player,
                                                      final InteractionHand hand) {
        final ItemStack stack = player.m_21120_(hand); // getItemInHand
        if (level.m_5776_()) { // isClientSide
            return InteractionResultHolder.m_19092_(stack, true); // sidedSuccess
        }

        final SololevelingModVariables.PlayerVariables vars =
                ((ICapabilityProvider) player)
                        .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                        .orElse(null);
        if (vars == null) {
            return InteractionResultHolder.m_19100_(stack); // fail - Solo Leveling not on this player
        }

        final double refund = vars.Vitality + vars.Strength + vars.Intelligence
                + vars.perception + vars.Speed + vars.Durability;
        if (refund <= 0.0D) {
            // m_5661_ = displayClientMessage, m_237113_ = Component.literal, m_130944_ = withStyle
            player.m_5661_(Component.m_237113_("No stat points to redistribute.")
                    .m_130944_(ChatFormatting.GRAY), true);
            return InteractionResultHolder.m_19100_(stack); // fail - do not consume
        }

        vars.SkillPoints += refund;
        vars.Vitality = 0.0D;
        vars.Strength = 0.0D;
        vars.Intelligence = 0.0D;
        vars.perception = 0.0D;
        vars.Speed = 0.0D;
        vars.Durability = 0.0D;
        vars.syncPlayerVariables(player);

        player.m_5661_(Component.m_237113_((int) refund + " stat points returned to SP.")
                .m_130944_(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        playChime(level, player);
        stack.m_41774_(1); // shrink

        return InteractionResultHolder.m_19092_(stack, false);
    }

    // m_7373_ = appendHoverText
    @Override
    public void m_7373_(final ItemStack stack, final Level level, final List<Component> tooltip,
                        final TooltipFlag flag) {
        tooltip.add(Component.m_237113_("Returns every allocated stat point to SP.")
                .m_130944_(ChatFormatting.GRAY));
        tooltip.add(Component.m_237113_("Level, rank and job are untouched.")
                .m_130944_(ChatFormatting.DARK_GRAY));
    }

    /** Registry-id sound lookup: the SoundEvents constant holder is SRG-mangled, same as UltraInstinct. */
    private static void playChime(final Level level, final Player player) {
        final SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(
                new ResourceLocation("block.enchantment_table.use"));
        if (sound != null) {
            level.m_6263_(null, player.m_20185_(), player.m_20186_(), player.m_20189_(),
                    sound, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }
}
