package dev.alshabab.shababparty;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Sends the recorder's decoded voice to listeners as it arrives, so the owner can hear the server live.
 *
 * Listens on 127.0.0.1 only; the owner reaches it through ssh (ssh -W 127.0.0.1:port), never from the
 * open internet. Every listener gets every frame:
 *
 *   u8  name length, name (UTF-8), u16 PCM byte count, PCM (48 kHz mono 16-bit little-endian)
 *
 * The voice thread never waits on a listener: each one has its own bounded queue and writer thread,
 * and frames are dropped for a listener whose connection cannot keep up.
 */
final class VoiceLiveStream {

    private static final Logger LOG = LogManager.getLogger("shababparty-voice");
    private static final int QUEUE_FRAMES = 250;   // about 5 s of one speaker at 20 ms a frame

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private ServerSocket server;

    void start(int port) {
        try {
            server = new ServerSocket(port, 4, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            LOG.error("voice live stream: could not listen on 127.0.0.1:{}", port, e);
            return;
        }
        Thread accept = new Thread(this::acceptLoop, "shabab-voice-live-accept");
        accept.setDaemon(true);
        accept.start();
        LOG.info("voice live stream on 127.0.0.1:{}", port);
    }

    boolean hasListeners() {
        return !listeners.isEmpty();
    }

    void send(String name, short[] pcm) {
        if (listeners.isEmpty()) {
            return;
        }
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        int nameLen = Math.min(nameBytes.length, 255);
        int pcmLen = Math.min(pcm.length * 2, 65534);
        byte[] frame = new byte[1 + nameLen + 2 + pcmLen];
        int o = 0;
        frame[o++] = (byte) nameLen;
        System.arraycopy(nameBytes, 0, frame, o, nameLen);
        o += nameLen;
        frame[o++] = (byte) ((pcmLen >> 8) & 0xFF);
        frame[o++] = (byte) (pcmLen & 0xFF);
        for (int i = 0; i < pcmLen / 2; i++) {
            frame[o++] = (byte) (pcm[i] & 0xFF);
            frame[o++] = (byte) ((pcm[i] >> 8) & 0xFF);
        }
        for (Listener l : listeners) {
            l.queue.offer(frame);   // full queue: this listener is lagging, drop the frame for it
        }
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket s = server.accept();
                s.setTcpNoDelay(true);
                Listener l = new Listener(s);
                listeners.add(l);
                Thread t = new Thread(() -> writeLoop(l), "shabab-voice-live-out");
                t.setDaemon(true);
                t.start();
                LOG.info("voice live stream: listener connected ({} now)", listeners.size());
            } catch (IOException e) {
                if (!server.isClosed()) {
                    LOG.warn("voice live stream: accept failed: {}", e.toString());
                }
            }
        }
    }

    private void writeLoop(Listener l) {
        try (Socket s = l.socket;
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()))) {
            while (!s.isClosed()) {
                byte[] frame = l.queue.poll(5, TimeUnit.SECONDS);
                if (frame == null) {
                    // Keep-alive: a frame with an empty name and no audio, so a dead link is noticed.
                    out.write(new byte[] {0, 0, 0});
                } else {
                    out.write(frame);
                }
                if (l.queue.isEmpty()) {
                    out.flush();
                }
            }
        } catch (IOException | InterruptedException e) {
            // listener went away
        } finally {
            listeners.remove(l);
            LOG.info("voice live stream: listener left ({} now)", listeners.size());
        }
    }

    private static final class Listener {
        final Socket socket;
        final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_FRAMES);

        Listener(Socket socket) {
            this.socket = socket;
        }
    }
}
