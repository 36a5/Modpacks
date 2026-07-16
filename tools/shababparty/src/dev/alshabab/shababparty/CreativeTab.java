package dev.alshabab.shababparty;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Puts the Reborn Elixir in the Tools & Utilities creative tab.
 *
 * This is not cosmetic: JEI builds its ingredient list from the creative tabs, so an item in no tab
 * is invisible to JEI search - players could craft the elixir but never find it by name. Adding it
 * to a tab is what makes "reborn" findable in the JEI search bar and its recipe reachable with R.
 *
 * MOD bus, not FORGE: tab contents are collected during mod construction, like registries.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CreativeTab {

    private CreativeTab() {
    }

    /** minecraft:tools_and_utilities. Matched by location because the CreativeModeTabs constant
     *  holder's fields are private in the SRG jar we compile against. */
    private static final ResourceLocation TOOLS_TAB = new ResourceLocation("minecraft", "tools_and_utilities");

    @SubscribeEvent
    public static void onBuildContents(final BuildCreativeModeTabContentsEvent event) {
        // m_135782_ = ResourceKey.location(). accept takes a Supplier<ItemLike>, which is exactly
        // what a RegistryObject is.
        if (TOOLS_TAB.equals(event.getTabKey().m_135782_())) {
            event.accept(ShababParty.STAT_REDISTRIBUTION);
        }
    }
}
