package dev.alshabab.shababparty.client;

import dev.alshabab.shababparty.ShababParty;
import dev.alshabab.shababparty.network.Net;
import dev.alshabab.shababparty.network.ReadyPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tells the server the moment the player takes control after joining: any key held, the mouse
 * moved, or any screen open that is not a loading screen. Sent once per join. The server side is
 * {@link dev.alshabab.shababparty.JoinProtection}.
 *
 * <p>Mouse look is compared per tick and only after two seconds, so the server's own position
 * sync right after joining does not count as the player looking around.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, value = Dist.CLIENT)
public final class JoinInput {

    private static final int SETTLE_TICKS = 40;

    private static boolean sent = true;
    private static int ticks;
    private static float yaw;
    private static float pitch;

    private JoinInput() {
    }

    @SubscribeEvent
    public static void onLoggingIn(final ClientPlayerNetworkEvent.LoggingIn event) {
        sent = false;
        ticks = 0;
    }

    @SubscribeEvent
    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        sent = true;
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sent) {
            return;
        }
        final Minecraft mc = Minecraft.m_91087_(); // getInstance
        final LocalPlayer player = mc.f_91074_; // player
        if (player == null) {
            return;
        }
        ticks++;
        final float newYaw = player.m_146908_(); // getYRot
        final float newPitch = player.m_146909_(); // getXRot
        boolean input = false;
        final Screen screen = mc.f_91080_; // screen
        if (screen == null) {
            for (KeyMapping key : mc.f_91066_.f_92059_) { // options.keyMappings
                if (key.m_90857_()) { // isDown
                    input = true;
                    break;
                }
            }
            if (!input && ticks > SETTLE_TICKS
                    && (Math.abs(newYaw - yaw) > 0.01f || Math.abs(newPitch - pitch) > 0.01f)) {
                input = true;
            }
        } else if (!isLoadingScreen(screen)) {
            input = true; // inventory, chat, pause menu: the player is here
        }
        yaw = newYaw;
        pitch = newPitch;
        if (input) {
            sent = true;
            Net.toServer(new ReadyPacket());
        }
    }

    private static boolean isLoadingScreen(final Screen screen) {
        return screen instanceof ReceivingLevelScreen
                || screen instanceof ProgressScreen
                || screen instanceof LevelLoadingScreen
                || screen instanceof GenericDirtMessageScreen
                || screen instanceof ConnectScreen;
    }
}
