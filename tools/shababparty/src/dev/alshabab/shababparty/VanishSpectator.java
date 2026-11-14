package dev.alshabab.shababparty;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import redstonedubstep.mods.vanishmod.api.PlayerVanishEvent;

/**
 * Puts a player into spectator the moment they vanish, and back into creative when they unvanish.
 *
 * Vanishmod's own /vanish prints "<name> left the game" and fires {@link PlayerVanishEvent}; we react
 * to that event, so this works with the raw /vanish command (no datapack function or selector needed).
 * Doing it in code also sidesteps Vanishmod's disableCommandTargeting, which hides a vanished player
 * from @s and @a so a command-based "gamemode spectator @s" after vanishing matches nobody.
 *
 * Not an @EventBusSubscriber on purpose. Vanishmod is server-only in the pack, and auto-subscription
 * reflects over this class on every side, which throws NoClassDefFoundError for PlayerVanishEvent on
 * clients and takes the whole mod down. ShababParty registers it only when Vanishmod is loaded.
 */
public final class VanishSpectator {

    private VanishSpectator() {
    }

    @SubscribeEvent
    public static void onVanish(final PlayerVanishEvent event) {
        if (!ShababParty.Config.VANISH_SPECTATOR_ENABLED.get()) {
            return;
        }
        final Player player = event.getEntity();
        if (!(player instanceof ServerPlayer server)) {
            return;  // client-side copy of the event; ignore
        }
        // m_143403_ = setGameMode. Spectator on vanish keeps voice chat (spectator_interaction=true).
        server.m_143403_(event.isVanished() ? GameType.SPECTATOR : GameType.CREATIVE);
    }
}
