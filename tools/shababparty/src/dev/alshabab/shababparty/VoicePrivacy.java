package dev.alshabab.shababparty;

import com.mojang.authlib.GameProfile;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.entity.player.Player;

/**
 * Lets only chosen players hear chosen speakers in Simple Voice Chat.
 *
 * Simple Voice Chat posts one SoundPacketEvent per listener for every piece of audio it is about
 * to send: EntitySoundPacketEvent for normal proximity talk, LocationalSoundPacketEvent and
 * StaticSoundPacketEvent for groups, spectators and the like. Cancelling the event drops that
 * audio for that one listener only. It works both ways: a player on [voicePrivacy]
 * privateSpeakers is heard only by players on allowedListeners, and hears only them in return.
 *
 * This only touches what is sent to other players. The speaker's own microphone stream, which
 * VoiceRecorder reads through MicrophonePacketEvent, is unaffected.
 *
 * The lists are read on every packet, so editing the config while the server runs takes effect as
 * soon as Forge reloads the file.
 */
@ForgeVoicechatPlugin
public class VoicePrivacy implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "shababparty_voice_privacy";
    }

    @Override
    public void registerEvents(final EventRegistration reg) {
        reg.registerEvent(EntitySoundPacketEvent.class, this::filter);
        reg.registerEvent(LocationalSoundPacketEvent.class, this::filter);
        reg.registerEvent(StaticSoundPacketEvent.class, this::filter);
    }

    private void filter(final SoundPacketEvent<?> event) {
        final String speaker = nameOf(event.getSenderConnection());
        final String listener = nameOf(event.getReceiverConnection());
        if (speaker == null || listener == null) {
            return; // audio a plugin plays, not a player talking
        }
        final List<? extends String> privates = ShababParty.Config.VOICE_PRIVATE_SPEAKERS.get();
        final List<? extends String> allowed = ShababParty.Config.VOICE_PRIVATE_LISTENERS.get();
        // Both ways: nobody outside the allowed list hears a private player, and a private player
        // hears nobody outside it either.
        if (contains(privates, speaker) && !contains(allowed, listener)) {
            event.cancel();
        } else if (contains(privates, listener) && !contains(allowed, speaker)) {
            event.cancel();
        }
    }

    private static boolean contains(final List<? extends String> names, final String name) {
        final String wanted = name.toLowerCase(Locale.ROOT);
        for (final String n : names) {
            if (n != null && n.toLowerCase(Locale.ROOT).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    private static String nameOf(final VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null) {
            return null;
        }
        final Object handle = connection.getPlayer().getPlayer();
        if (handle instanceof Player player) {
            final GameProfile profile = player.m_36316_(); // getGameProfile
            return profile == null ? null : profile.getName();
        }
        return null;
    }
}
