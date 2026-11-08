package dev.alshabab.shababparty;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import vazkii.patchouli.api.PatchouliAPI;

/**
 * The Shabab Guide as its own item. Patchouli's shared guide_book item tells books apart only by
 * NBT, which JEI ignores, so the guide never showed up when searching JEI. A real item with
 * custom_book_item set in book.json shows up, carries a normal crafting recipe, and opens the
 * book through Patchouli's API on right-click.
 */
public final class GuideBookItem extends Item {

    public static final ResourceLocation BOOK = new ResourceLocation(ShababParty.MOD_ID, "guide");

    public GuideBookItem() {
        super(new Item.Properties().m_41487_(1)); // stacksTo(1)
    }

    // m_7203_ = use(Level, Player, InteractionHand)
    @Override
    public InteractionResultHolder<ItemStack> m_7203_(final Level level, final Player player,
            final InteractionHand hand) {
        final ItemStack stack = player.m_21120_(hand); // getItemInHand
        if (player instanceof ServerPlayer serverPlayer) {
            PatchouliAPI.get().openBookGUI(serverPlayer, BOOK);
        }
        return InteractionResultHolder.m_19092_(stack, level.m_5776_()); // sidedSuccess
    }
}
