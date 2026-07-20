package dev.alshabab.shababparty.client;

import dev.alshabab.shababparty.ShababParty;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The two damage-number binds.
 *
 * <p>Both are real KeyMappings rather than raw key polling, which is what makes them appear in
 * Options -> Controls -> Shabab Party and be rebindable there. Minecraft already handles conflict
 * detection and persistence, so none of that needs writing here.
 *
 * <p>The keycodes are raw ints because org.lwjgl is not on this build's classpath and must not be
 * added -- the three-arg KeyMapping constructor takes a keycode directly, sidestepping
 * InputConstants and GLFW entirely.
 */
public final class DamageNumberKeys {

    private static final String CATEGORY = "key.categories.shababparty";

    /** GLFW_KEY_KP_5. */
    private static final int NUMPAD_5 = 325;
    /** GLFW_KEY_KP_6. */
    private static final int NUMPAD_6 = 326;

    public static final KeyMapping TOGGLE =
            new KeyMapping("key.shababparty.toggle_damage_numbers", NUMPAD_5, CATEGORY);

    public static final KeyMapping CONFIG =
            new KeyMapping("key.shababparty.damage_numbers_config", NUMPAD_6, CATEGORY);

    private DamageNumberKeys() {
    }

    @Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {

        private Registration() {
        }

        @SubscribeEvent
        public static void onRegisterKeys(final RegisterKeyMappingsEvent event) {
            event.register(TOGGLE);
            event.register(CONFIG);
        }
    }

    @Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, value = Dist.CLIENT)
    public static final class Handler {

        private Handler() {
        }

        @SubscribeEvent
        public static void onClientTick(final TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            final Minecraft mc = Minecraft.m_91087_();
            if (mc.f_91074_ == null) {
                return;
            }

            while (TOGGLE.m_90859_()) {
                final boolean now = !ClientConfig.ENABLED.get();
                ClientConfig.ENABLED.set(now);
                ClientConfig.ENABLED.save();
                // A toggle whose only feedback is the absence of numbers in a quiet moment is
                // indistinguishable from a toggle that did nothing.
                mc.f_91074_.m_5661_(Component.m_237113_(
                        now ? "Damage numbers: on" : "Damage numbers: off"), true);
            }

            while (CONFIG.m_90859_()) {
                mc.m_91152_(new DamageNumbersScreen(null));
            }
        }
    }
}
