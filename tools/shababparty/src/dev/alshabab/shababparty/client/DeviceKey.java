package dev.alshabab.shababparty.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.HexFormat;

import dev.alshabab.shababparty.ShababParty;
import dev.alshabab.shababparty.network.DeviceHelloPacket;
import dev.alshabab.shababparty.network.Net;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * This computer's device key, sent to the server on every join so it can skip the password on a
 * device the player has used before. See {@link dev.alshabab.shababparty.DeviceLogin}.
 *
 * <p>The key is 32 random bytes kept in the OS user's home folder, not in the Minecraft instance,
 * so reinstalling or updating the modpack keeps it. Deleting the file makes this computer a new
 * device again.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, value = Dist.CLIENT)
public final class DeviceKey {

    private static String cached;

    private DeviceKey() {
    }

    @SubscribeEvent
    public static void onLoggingIn(final ClientPlayerNetworkEvent.LoggingIn event) {
        final String key = get();
        if (key != null) {
            Net.toServer(new DeviceHelloPacket(key));
        }
    }

    private static String get() {
        if (cached != null) {
            return cached;
        }
        final Path file = Paths.get(System.getProperty("user.home"), ".shababparty", "device-id");
        try {
            if (Files.isRegularFile(file)) {
                final String existing = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (existing.matches("[0-9a-f]{64}")) {
                    cached = existing;
                    return cached;
                }
            }
            final byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            final String fresh = HexFormat.of().formatHex(bytes);
            Files.createDirectories(file.getParent());
            Files.writeString(file, fresh, StandardCharsets.UTF_8);
            cached = fresh;
            return cached;
        } catch (IOException | RuntimeException e) {
            ShababParty.LOGGER.warn("Could not read or create the device key at {}", file, e);
            return null;
        }
    }
}
