package dev.alshabab.shababparty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.HexFormat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Skips the Auth password for a player joining from a device they have already logged in from.
 *
 * <p>The server cannot see a MAC address (it never leaves the local network) and an IP address is
 * a poor identity here: home IPs change, and everyone in one house shares one. So each client
 * makes a random 32-byte key once, keeps it in the OS user's home folder (see
 * {@link dev.alshabab.shababparty.client.DeviceKey}) and sends it on every join.
 *
 * <p>The server only ever stores a SHA-256 hash of that key, per player name:
 * <ul>
 *   <li>When a player finishes a normal password login (Auth adds the auth.logged tag), the
 *       device they are on is remembered for that name.</li>
 *   <li>When they join again with a remembered device, the server runs Auth's own
 *       {@code function auth:login} for them, exactly what a correct password does.</li>
 *   <li>A brand new name (never registered, not an operator) is registered on its first device
 *       without /register: see {@link #claimName}. Switch off with claimNewNames.</li>
 *   <li>A new device for a registered name, or a client without this mod, falls through to the
 *       normal password prompt. Nothing about Auth itself changes.</li>
 * </ul>
 *
 * <p>Remembered devices expire after trustDays without use. Operators can clear a player's
 * devices with {@code /devices forget <player>}, for example after a password change or if a
 * player logged in on a friend's computer.
 *
 * <p>Stored in shababparty-devices.properties in the world folder.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeviceLogin {

    private static final String FILE_NAME = "shababparty-devices.properties";

    /**
     * Ticks between the key arriving and the auto-login. Auth's leave function, which removes
     * auth.logged from a returning player, runs on their first tick back; logging them in before
     * that would be undone at once.
     */
    private static final int LOGIN_DELAY_TICKS = 40;
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private static final String TAG_REGISTERED = "auth.registered";
    private static final String TAG_LOGGED = "auth.logged";
    private static final String TAG_BYPASS = "auth.bypass";

    /** lower-case name to (device hash to last-used time in millis). */
    private static final Map<String, Map<String, Long>> TRUSTED = new TreeMap<>();
    /** Device hash of each online player that sent one this session. */
    private static final Map<UUID, String> ONLINE_KEYS = new HashMap<>();
    /** Online players waiting for the auto-login check, with ticks left. */
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private static final SecureRandom RANDOM = new SecureRandom();

    private static File file;
    private static int tickCounter;

    private DeviceLogin() {
    }

    // ---------------------------------------------------------------- storage

    @SubscribeEvent
    public static void onServerStarted(final ServerStartedEvent event) {
        // m_129843_ = getWorldPath; the stats folder sits directly in the world folder.
        file = new File(event.getServer().m_129843_(LevelResource.f_78175_).toFile().getParentFile(), FILE_NAME);
        TRUSTED.clear();
        ONLINE_KEYS.clear();
        PENDING.clear();
        if (!file.isFile()) {
            return;
        }
        final Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        } catch (IOException e) {
            ShababParty.LOGGER.error("Could not read {}", file, e);
            return;
        }
        for (String name : props.stringPropertyNames()) {
            final Map<String, Long> devices = new HashMap<>();
            for (String entry : props.getProperty(name).split(",")) {
                final int colon = entry.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                try {
                    devices.put(entry.substring(0, colon).trim(), Long.parseLong(entry.substring(colon + 1).trim()));
                } catch (NumberFormatException ignored) {
                    // a hand-edited line we cannot read; skip it rather than refuse the whole file
                }
            }
            if (!devices.isEmpty()) {
                TRUSTED.put(name.toLowerCase(), devices);
            }
        }
        if (pruneExpired()) {
            save();
        }
    }

    private static void save() {
        if (file == null) {
            return;
        }
        final Properties props = new Properties();
        for (Map.Entry<String, Map<String, Long>> e : TRUSTED.entrySet()) {
            final StringBuilder value = new StringBuilder();
            for (Map.Entry<String, Long> d : e.getValue().entrySet()) {
                if (value.length() > 0) {
                    value.append(',');
                }
                value.append(d.getKey()).append(':').append(d.getValue());
            }
            props.setProperty(e.getKey(), value.toString());
        }
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            props.store(out, "Remembered devices per player (SHA-256 of the device key : last used). "
                    + "Clear one player with /devices forget <player>.");
        } catch (IOException e) {
            ShababParty.LOGGER.error("Could not write {}", file, e);
        }
    }

    /** Drops devices unused for longer than trustDays. Returns true if anything was removed. */
    private static boolean pruneExpired() {
        final long cutoff = System.currentTimeMillis()
                - ShababParty.Config.DEVICE_LOGIN_TRUST_DAYS.get() * DAY_MILLIS;
        boolean changed = false;
        final Iterator<Map.Entry<String, Map<String, Long>>> players = TRUSTED.entrySet().iterator();
        while (players.hasNext()) {
            final Map<String, Long> devices = players.next().getValue();
            changed |= devices.values().removeIf(lastUsed -> lastUsed < cutoff);
            if (devices.isEmpty()) {
                players.remove();
                changed = true;
            }
        }
        return changed;
    }

    // ---------------------------------------------------------------- joining

    /** Called from DeviceHelloPacket on the server thread. */
    public static void onHello(final ServerPlayer player, final String key) {
        if (!ShababParty.Config.DEVICE_LOGIN_ENABLED.get() || key == null || !key.matches("[0-9a-f]{64}")) {
            return;
        }
        final String hash = sha256(key);
        if (hash == null) {
            return;
        }
        ONLINE_KEYS.put(player.m_20148_(), hash); // getUUID
        PENDING.put(player.m_20148_(), LOGIN_DELAY_TICKS);
    }

    @SubscribeEvent
    public static void onLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        ONLINE_KEYS.remove(event.getEntity().m_20148_());
        PENDING.remove(event.getEntity().m_20148_());
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ONLINE_KEYS.isEmpty()
                || !ShababParty.Config.DEVICE_LOGIN_ENABLED.get()) {
            return;
        }
        final MinecraftServer server = event.getServer();

        final Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, Integer> e = it.next();
            final int left = e.getValue() - 1;
            if (left > 0) {
                e.setValue(left);
                continue;
            }
            it.remove();
            final ServerPlayer player = server.m_6846_().m_11259_(e.getKey()); // getPlayerList().getPlayer(uuid)
            if (player != null) {
                tryAutoLogin(server, player);
            }
        }

        // Once a second, remember the device of anyone who has just logged in with a password.
        if (++tickCounter % 20 == 0) {
            for (ServerPlayer player : server.m_6846_().m_11314_()) { // getPlayers()
                rememberIfLogged(player);
            }
        }
    }

    private static void tryAutoLogin(final MinecraftServer server, final ServerPlayer player) {
        final Set<String> tags = player.m_19880_(); // getTags
        if (tags.contains(TAG_LOGGED) || tags.contains(TAG_BYPASS)) {
            return; // already in, or an Auth bypass account
        }
        final String hash = ONLINE_KEYS.get(player.m_20148_());
        if (hash == null) {
            return;
        }
        if (!tags.contains(TAG_REGISTERED)) {
            claimName(server, player, hash);
            return;
        }
        final Map<String, Long> devices = TRUSTED.get(player.m_6302_().toLowerCase()); // getScoreboardName
        if (devices == null) {
            return;
        }
        final Long lastUsed = devices.get(hash);
        final long now = System.currentTimeMillis();
        if (lastUsed == null || lastUsed < now - ShababParty.Config.DEVICE_LOGIN_TRUST_DAYS.get() * DAY_MILLIS) {
            return;
        }
        devices.put(hash, now);
        save();

        runAs(server, player, "function auth:login");
        player.m_213846_(Component.m_237113_("§aWelcome back. Logged in automatically on this device.")); // sendSystemMessage
        ShababParty.LOGGER.info("Device login for {}", player.m_6302_());
    }

    /**
     * First join of a new name: no /register. Auth keeps the password as a number in the register
     * score, and its own tick registers anyone whose register score is non-zero and then logs them
     * in. Setting that score to a random number nobody knows registers the player without a
     * password prompt, and remembering this device at the same moment means it is the only one that
     * gets in without one. Anyone else joining under the name later meets Auth's normal login
     * prompt and a password they cannot know.
     */
    private static void claimName(final MinecraftServer server, final ServerPlayer player, final String hash) {
        if (!ShababParty.Config.DEVICE_LOGIN_CLAIM_NEW_NAMES.get() || player.m_20310_(2)) { // hasPermissions
            return; // switched off, or an operator: they keep the normal /register
        }
        final int password = 1 + RANDOM.nextInt(Integer.MAX_VALUE - 1);
        runAs(server, player, "scoreboard players set @s register " + password);

        TRUSTED.computeIfAbsent(player.m_6302_().toLowerCase(), k -> new HashMap<>())
                .put(hash, System.currentTimeMillis());
        save();
        player.m_213846_(Component.m_237113_(
                "§aWelcome! This computer now owns the name " + player.m_6302_()
                        + ", so you never need a password here.\n"
                        + "§7Want to play from another computer too? On this one, type "
                        + "§f/trigger changepassword set <numbers>§7 and use that password there."));
        ShababParty.LOGGER.info("{} claimed their name on a new device without /register", player.m_6302_());
    }

    /**
     * Runs a command as the player with permission level 2 and no chat feedback. m_129892_ =
     * getCommands, m_230957_ = performPrefixedCommand, m_20203_ = createCommandSourceStack,
     * m_81324_ = withSuppressedOutput, m_81325_ = withPermission.
     */
    private static void runAs(final MinecraftServer server, final ServerPlayer player, final String command) {
        server.m_129892_().m_230957_(player.m_20203_().m_81324_().m_81325_(2), command);
    }

    private static void rememberIfLogged(final ServerPlayer player) {
        final Set<String> tags = player.m_19880_();
        if (!tags.contains(TAG_LOGGED) || tags.contains(TAG_BYPASS)) {
            return;
        }
        // A returning player still carries auth.logged from their last visit until Auth's leave
        // function clears it on their first tick back. Waiting out the same delay as the auto-login
        // means a stale tag can never get a new device remembered without a password.
        if (PENDING.containsKey(player.m_20148_())) {
            return;
        }
        final String hash = ONLINE_KEYS.get(player.m_20148_());
        if (hash == null) {
            return; // client without the mod, or the key has not arrived yet
        }
        final Map<String, Long> devices = TRUSTED.computeIfAbsent(player.m_6302_().toLowerCase(), k -> new HashMap<>());
        if (devices.containsKey(hash)) {
            return;
        }
        devices.put(hash, System.currentTimeMillis());
        save();
        player.m_213846_(Component.m_237113_(
                "§aThis device is now remembered. Next time you join from it, you will not need your password."));
        ShababParty.LOGGER.info("Remembered a new device for {}", player.m_6302_());
    }

    private static String sha256(final String key) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            ShababParty.LOGGER.error("SHA-256 unavailable", e);
            return null;
        }
    }

    // ---------------------------------------------------------------- command

    private static final SuggestionProvider<CommandSourceStack> SAVED_NAMES = (ctx, builder) -> {
        for (String name : TRUSTED.keySet()) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.m_82127_("devices")
                        .requires(src -> src.m_6761_(2))
                        .executes(DeviceLogin::list)
                        .then(Commands.m_82127_("list")
                                .executes(DeviceLogin::list))
                        .then(Commands.m_82127_("forget")
                                .then(Commands.m_82129_("player", StringArgumentType.word())
                                        .suggests(SAVED_NAMES)
                                        .executes(DeviceLogin::forget))));
    }

    private static int list(final CommandContext<CommandSourceStack> ctx) {
        if (pruneExpired()) {
            save();
        }
        if (TRUSTED.isEmpty()) {
            ok(ctx, "No remembered devices yet. A device is remembered after a normal password login.");
            return 1;
        }
        final long now = System.currentTimeMillis();
        final StringBuilder out = new StringBuilder("Remembered devices:");
        for (Map.Entry<String, Map<String, Long>> e : TRUSTED.entrySet()) {
            long newest = 0L;
            for (long t : e.getValue().values()) {
                newest = Math.max(newest, t);
            }
            out.append("\n  ").append(e.getKey()).append(": ").append(e.getValue().size())
                    .append(e.getValue().size() == 1 ? " device" : " devices")
                    .append(", last used ").append((now - newest) / DAY_MILLIS).append(" days ago");
        }
        out.append("\nForget one player's devices with /devices forget <player>");
        ok(ctx, out.toString());
        return 1;
    }

    private static int forget(final CommandContext<CommandSourceStack> ctx) {
        final String name = StringArgumentType.getString(ctx, "player");
        if (TRUSTED.remove(name.toLowerCase()) == null) {
            ctx.getSource().m_81352_(Component.m_237113_(name + " has no remembered devices.")); // sendFailure
            return 0;
        }
        save();
        ok(ctx, "Forgot every device for " + name + ". They need their password on the next join.");
        return 1;
    }

    private static void ok(final CommandContext<CommandSourceStack> ctx, final String text) {
        ctx.getSource().m_288197_(() -> Component.m_237113_(text), false); // sendSuccess
    }
}
