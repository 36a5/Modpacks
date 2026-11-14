package dev.alshabab.shababparty;

import com.mojang.authlib.GameProfile;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Records every player's Simple Voice Chat microphone audio to per-player WAV files on the server.
 *
 * This is a private family server; the people on it know their voice is captured for a home AI
 * project and agreed to it. The recorder is OFF by default and only runs when
 * [voiceRecording] enabled=true is set in the shababparty config, so it never records silently on
 * a server where that has not been turned on deliberately.
 *
 * Simple Voice Chat ships each 20 ms of microphone audio as one Opus-encoded, per-connection packet.
 * We decode each packet to 48 kHz mono 16-bit PCM (one Opus decoder per speaker, because the decoder
 * is stateful) and append it to that speaker's open WAV file. When a speaker goes quiet for longer
 * than the split gap the file is closed, so each stretch of talking lands in its own timestamped
 * file ready to feed a trainer.
 *
 * Simple Voice Chat discovers this through the @ForgeVoicechatPlugin annotation; nothing in the rest
 * of the mod has to register it.
 */
@ForgeVoicechatPlugin
public class VoiceRecorder implements de.maxhenkel.voicechat.api.VoicechatPlugin {

    private static final Logger LOG = LogManager.getLogger("shababparty-voice");
    private static final int SAMPLE_RATE = 48000;      // Simple Voice Chat always runs at 48 kHz.
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private VoicechatApi api;
    private Path baseDir;
    private final Map<UUID, Speaker> speakers = new ConcurrentHashMap<>();
    private ScheduledExecutorService janitor;
    private final VoiceLiveStream live = new VoiceLiveStream();

    @Override
    public String getPluginId() {
        return "shababparty";
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.api = api;
        // The server process runs from its run/ directory, so this lands at run/voice-recordings.
        this.baseDir = Paths.get("voice-recordings").toAbsolutePath();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            LOG.error("voice: could not create {}", baseDir, e);
        }
        janitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "shabab-voice-janitor");
            t.setDaemon(true);
            return t;
        });
        // Close files when a speaker stops talking, even if no more packets arrive to trigger it.
        janitor.scheduleAtFixedRate(this::closeIdle, 1, 1, TimeUnit.SECONDS);
        // Flush everything if the JVM is stopping (stop command, crash, reboot).
        Runtime.getRuntime().addShutdownHook(new Thread(this::closeAll, "shabab-voice-flush"));
        int livePort = ShababParty.Config.VOICE_LIVE_PORT.get();
        if (livePort > 0) {
            live.start(livePort);
        }
        LOG.info("voice recorder loaded; recording {} to {}",
                ShababParty.Config.VOICE_RECORDING_ENABLED.get() ? "ON" : "OFF (set [voiceRecording] enabled=true)",
                baseDir);
    }

    @Override
    public void registerEvents(EventRegistration reg) {
        reg.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        reg.registerEvent(PlayerDisconnectedEvent.class, this::onDisconnect);
        reg.registerEvent(VoicechatServerStartedEvent.class, e ->
                LOG.info("voice recorder attached to the voice chat server"));
    }

    private boolean enabled() {
        return ShababParty.Config.VOICE_RECORDING_ENABLED.get();
    }

    private long gapMs() {
        return (long) (ShababParty.Config.VOICE_RECORDING_SPLIT_SECONDS.get() * 1000.0D);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        if (!enabled()) {
            return;
        }
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || sender.getPlayer() == null) {
            return;  // whisper test packets and the like have no sender
        }
        byte[] opus = event.getPacket().getOpusEncodedData();
        if (opus == null || opus.length == 0) {
            return;  // an empty packet marks the end of talking, not audio
        }
        UUID id = sender.getPlayer().getUuid();
        Speaker speaker = speakers.computeIfAbsent(id, u -> new Speaker(api.createDecoder(), nameOf(sender, u)));
        synchronized (speaker) {
            try {
                short[] pcm = speaker.decoder.decode(opus);
                if (pcm == null || pcm.length == 0) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (speaker.wav == null || now - speaker.lastWrite > gapMs()) {
                    speaker.close();
                    speaker.decoder.resetState();
                    speaker.open(baseDir);
                    LOG.info("VOICE-START {}", speaker.name);  // a live monitor tails these lines
                }
                speaker.write(pcm);
                speaker.lastWrite = now;
                live.send(speaker.name, pcm);
                // A rough loudness level (0-100) so a live meter has something to show, at most 5/sec
                // so it does not flood latest.log.
                if (now - speaker.lastLevelLog >= 200) {
                    speaker.lastLevelLog = now;
                    long sum = 0;
                    for (short s : pcm) {
                        sum += Math.abs((int) s);
                    }
                    int level = (int) Math.min(100, (sum / pcm.length) * 100L / 8000L);
                    LOG.info("VOICE-LEVEL {} {}", speaker.name, level);
                }
            } catch (Exception e) {
                LOG.error("voice: failed to record a packet from {}", speaker.name, e);
                speaker.close();
            }
        }
    }

    private void onDisconnect(PlayerDisconnectedEvent event) {
        Speaker speaker = speakers.remove(event.getPlayerUuid());
        if (speaker != null) {
            synchronized (speaker) {
                speaker.close();
                speaker.decoder.close();
            }
        }
    }

    private void closeIdle() {
        long now = System.currentTimeMillis();
        for (Speaker speaker : speakers.values()) {
            synchronized (speaker) {
                if (speaker.wav != null && now - speaker.lastWrite > gapMs()) {
                    speaker.close();
                }
            }
        }
    }

    private void closeAll() {
        for (Speaker speaker : speakers.values()) {
            synchronized (speaker) {
                speaker.close();
            }
        }
    }

    /** The account name if we can read it, otherwise the UUID. Used for the folder name. */
    private String nameOf(VoicechatConnection sender, UUID id) {
        try {
            Object mc = sender.getPlayer().getPlayer();  // the net.minecraft ServerPlayer
            if (mc instanceof net.minecraft.world.entity.player.Player p) {
                GameProfile profile = p.m_36316_();  // getGameProfile
                if (profile != null && profile.getName() != null && !profile.getName().isBlank()) {
                    return sanitize(profile.getName());
                }
            }
        } catch (Throwable ignored) {
            // fall through to the UUID
        }
        return id.toString();
    }

    private static String sanitize(String name) {
        String clean = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        return clean.isBlank() ? "player" : clean;
    }

    /** One speaker: a stateful Opus decoder and the WAV file currently being written for them. */
    private static final class Speaker {
        final OpusDecoder decoder;
        final String name;
        RandomAccessFile wav;
        Path path;
        long dataBytes;
        long lastWrite;
        long lastLevelLog;

        Speaker(OpusDecoder decoder, String name) {
            this.decoder = decoder;
            this.name = name;
        }

        void open(Path baseDir) throws IOException {
            Path dir = baseDir.resolve(name);
            Files.createDirectories(dir);
            path = dir.resolve(name + "_" + LocalDateTime.now().format(STAMP) + ".wav");
            // Keep going if two files land in the same second.
            int n = 1;
            while (Files.exists(path)) {
                path = dir.resolve(name + "_" + LocalDateTime.now().format(STAMP) + "_" + (n++) + ".wav");
            }
            wav = new RandomAccessFile(path.toFile(), "rw");
            writeHeader(wav, 0);
            dataBytes = 0;
        }

        void write(short[] pcm) throws IOException {
            byte[] bytes = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) {
                bytes[i * 2] = (byte) (pcm[i] & 0xFF);
                bytes[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
            }
            wav.write(bytes);
            dataBytes += bytes.length;
        }

        void close() {
            if (wav == null) {
                return;
            }
            try {
                wav.seek(0);
                writeHeader(wav, dataBytes);   // fill in the real sizes now that we know them
                wav.close();
            } catch (IOException ignored) {
                // nothing useful to do; the file still holds the audio minus correct sizes
            }
            wav = null;
            path = null;
        }

        /** 44-byte canonical WAV header for 48 kHz mono 16-bit PCM. */
        private static void writeHeader(RandomAccessFile f, long dataLen) throws IOException {
            int byteRate = SAMPLE_RATE * 2;  // mono, 2 bytes per sample
            byte[] h = new byte[44];
            putAscii(h, 0, "RIFF");
            putInt(h, 4, (int) (36 + dataLen));
            putAscii(h, 8, "WAVE");
            putAscii(h, 12, "fmt ");
            putInt(h, 16, 16);           // PCM fmt chunk size
            putShort(h, 20, (short) 1);  // PCM
            putShort(h, 22, (short) 1);  // channels
            putInt(h, 24, SAMPLE_RATE);
            putInt(h, 28, byteRate);
            putShort(h, 32, (short) 2);  // block align
            putShort(h, 34, (short) 16); // bits per sample
            putAscii(h, 36, "data");
            putInt(h, 40, (int) dataLen);
            f.write(h);
        }

        private static void putAscii(byte[] b, int off, String s) {
            for (int i = 0; i < s.length(); i++) {
                b[off + i] = (byte) s.charAt(i);
            }
        }

        private static void putInt(byte[] b, int off, int v) {
            b[off] = (byte) (v & 0xFF);
            b[off + 1] = (byte) ((v >> 8) & 0xFF);
            b[off + 2] = (byte) ((v >> 16) & 0xFF);
            b[off + 3] = (byte) ((v >> 24) & 0xFF);
        }

        private static void putShort(byte[] b, int off, short v) {
            b[off] = (byte) (v & 0xFF);
            b[off + 1] = (byte) ((v >> 8) & 0xFF);
        }
    }
}
