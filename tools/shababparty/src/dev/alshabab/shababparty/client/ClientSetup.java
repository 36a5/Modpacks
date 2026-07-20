package dev.alshabab.shababparty.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Client-only mod construction.
 *
 * <p>This exists purely to keep client types out of {@link dev.alshabab.shababparty.ShababParty}.
 * Registering the ConfigScreenFactory inline there put net/minecraft/client/gui/screens/Screen into
 * the constant pool of a class the dedicated server loads during mod construction -- lazy
 * resolution most likely covers it, but "most likely" is a poor foundation for server boot, and the
 * fix costs one small class.
 */
public final class ClientSetup {

    private ClientSetup() {
    }

    /** Called through DistExecutor, so this method only ever runs on a client. */
    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new DamageNumbersScreen(parent)));
    }
}
