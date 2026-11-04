package dev.alshabab.shababparty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Personal join messages, set per player by an operator.
 *
 * <pre>
 *   /welcome set &lt;player&gt; &lt;title&gt; | &lt;subtitle&gt;   set or replace a player's message
 *   /welcome off &lt;player&gt;                          remove it
 *   /welcome test &lt;player&gt;                         show it to them now (they must be online)
 *   /welcome list                                  every player with a message
 * </pre>
 *
 * The subtitle is optional. Colour codes use &amp; (&amp;6 gold, &amp;d pink, &amp;l bold, &amp;o italic). Without
 * any codes the title is gold and bold and the subtitle pink and italic.
 *
 * <p>The message is shown only to that player, about 10 seconds after they join, so the client has
 * finished loading and can actually display a title: a big title, a subtitle, the same text as one
 * chat line, and a level-up chime.
 *
 * <p>Players are matched by name, case-insensitively, because the server runs offline-mode and a
 * name is the only identity an operator knows before the player has ever joined. Messages live in
 * shababparty-welcome.properties in the world folder, so they belong to the world and survive
 * restarts and pack updates.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WelcomeMessages {

    private static final String FILE_NAME = "shababparty-welcome.properties";
    private static final int DELAY_TICKS = 200;
    private static final String SEPARATOR = "|";

    /** lower-case player name to "title|subtitle", as typed (with &amp; codes). */
    private static final Map<String, String> MESSAGES = new TreeMap<>();
    /** Players waiting for their message, by name, with ticks left. */
    private static final Map<String, Integer> PENDING = new HashMap<>();

    private static File file;

    private WelcomeMessages() {
    }

    // ---------------------------------------------------------------- storage

    @SubscribeEvent
    public static void onServerStarted(final ServerStartedEvent event) {
        // m_129843_ = getWorldPath. f_78175_ is the stats folder, which sits directly in the world
        // folder, so its parent is the world folder itself.
        file = new File(event.getServer().m_129843_(LevelResource.f_78175_).toFile().getParentFile(), FILE_NAME);
        MESSAGES.clear();
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
            MESSAGES.put(name.toLowerCase(), props.getProperty(name));
        }
    }

    private static void save() {
        if (file == null) {
            return;
        }
        final Properties props = new Properties();
        props.putAll(MESSAGES);
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            props.store(out, "Personal join messages. Edit in game with /welcome.");
        } catch (IOException e) {
            ShababParty.LOGGER.error("Could not write {}", file, e);
        }
    }

    // ---------------------------------------------------------------- showing

    @SubscribeEvent
    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            final String key = player.m_6302_().toLowerCase(); // getScoreboardName
            if (MESSAGES.containsKey(key)) {
                PENDING.put(key, DELAY_TICKS);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        final MinecraftServer server = event.getServer();
        final Iterator<Map.Entry<String, Integer>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<String, Integer> e = it.next();
            final int left = e.getValue() - 1;
            if (left > 0) {
                e.setValue(left);
                continue;
            }
            it.remove();
            final ServerPlayer player = online(server, e.getKey());
            final String message = MESSAGES.get(e.getKey());
            if (player != null && message != null) {
                show(player, message);
            }
        }
    }

    private static void show(final ServerPlayer player, final String message) {
        final int bar = message.indexOf(SEPARATOR);
        final String rawTitle = (bar < 0 ? message : message.substring(0, bar)).trim();
        final String rawSub = bar < 0 ? "" : message.substring(bar + 1).trim();

        final String title = styled(rawTitle, "§6§l");
        final String sub = rawSub.isEmpty() ? "" : styled(rawSub, "§d§o");

        // f_8906_ = connection, m_9829_ = send. Sent straight to this one player, never broadcast.
        player.f_8906_.m_9829_(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
        player.f_8906_.m_9829_(new ClientboundSetSubtitleTextPacket(Component.m_237113_(sub)));
        player.f_8906_.m_9829_(new ClientboundSetTitleTextPacket(Component.m_237113_(title)));

        final String line = "§c♥ §r" + title + (sub.isEmpty() ? "" : "§r " + sub) + "§r§c ♥";
        player.m_213846_(Component.m_237113_(line)); // sendSystemMessage

        final SoundEvent chime = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.player.levelup"));
        if (chime != null) {
            player.m_6330_(chime, SoundSource.MASTER, 1.0F, 1.2F); // playNotifySound: this player only
        }
    }

    /** & codes become section signs. Text with no codes of its own gets the default style. */
    private static String styled(final String raw, final String defaultStyle) {
        if (raw.contains("&")) {
            return raw.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
        }
        return defaultStyle + raw;
    }

    private static ServerPlayer online(final MinecraftServer server, final String lowerName) {
        for (ServerPlayer p : server.m_6846_().m_11314_()) { // getPlayerList().getPlayers()
            if (p.m_6302_().equalsIgnoreCase(lowerName)) {
                return p;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- command

    private static final SuggestionProvider<CommandSourceStack> ONLINE_NAMES = (ctx, builder) -> {
        for (ServerPlayer p : ctx.getSource().m_81377_().m_6846_().m_11314_()) {
            builder.suggest(p.m_6302_());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SAVED_NAMES = (ctx, builder) -> {
        for (String name : MESSAGES.keySet()) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.m_82127_("welcome")
                        .requires(src -> src.m_6761_(2))
                        .executes(WelcomeMessages::help)
                        .then(Commands.m_82127_("set")
                                .then(Commands.m_82129_("player", StringArgumentType.word())
                                        .suggests(ONLINE_NAMES)
                                        .then(Commands.m_82129_("message", StringArgumentType.greedyString())
                                                .executes(WelcomeMessages::set))))
                        .then(Commands.m_82127_("off")
                                .then(Commands.m_82129_("player", StringArgumentType.word())
                                        .suggests(SAVED_NAMES)
                                        .executes(WelcomeMessages::off)))
                        .then(Commands.m_82127_("test")
                                .then(Commands.m_82129_("player", StringArgumentType.word())
                                        .suggests(SAVED_NAMES)
                                        .executes(WelcomeMessages::test)))
                        .then(Commands.m_82127_("list")
                                .executes(WelcomeMessages::list)));
    }

    private static int set(final CommandContext<CommandSourceStack> ctx) {
        final String name = StringArgumentType.getString(ctx, "player");
        final String message = StringArgumentType.getString(ctx, "message").trim();
        MESSAGES.put(name.toLowerCase(), message);
        save();
        ok(ctx, "Welcome message for " + name + " set. Shown to them only, each time they join. "
                + "Try it with /welcome test " + name);
        return 1;
    }

    private static int off(final CommandContext<CommandSourceStack> ctx) {
        final String name = StringArgumentType.getString(ctx, "player");
        if (MESSAGES.remove(name.toLowerCase()) == null) {
            ctx.getSource().m_81352_(Component.m_237113_(name + " has no welcome message.")); // sendFailure
            return 0;
        }
        PENDING.remove(name.toLowerCase());
        save();
        ok(ctx, "Welcome message for " + name + " removed.");
        return 1;
    }

    private static int test(final CommandContext<CommandSourceStack> ctx) {
        final String name = StringArgumentType.getString(ctx, "player");
        final String message = MESSAGES.get(name.toLowerCase());
        if (message == null) {
            ctx.getSource().m_81352_(Component.m_237113_(name + " has no welcome message."));
            return 0;
        }
        final ServerPlayer player = online(ctx.getSource().m_81377_(), name);
        if (player == null) {
            ctx.getSource().m_81352_(Component.m_237113_(name + " is not online."));
            return 0;
        }
        show(player, message);
        ok(ctx, "Shown to " + player.m_6302_() + ".");
        return 1;
    }

    private static int list(final CommandContext<CommandSourceStack> ctx) {
        if (MESSAGES.isEmpty()) {
            ok(ctx, "No welcome messages set. Use /welcome set <player> <title> | <subtitle>");
            return 1;
        }
        final StringBuilder out = new StringBuilder("Welcome messages:");
        for (Map.Entry<String, String> e : MESSAGES.entrySet()) {
            out.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
        }
        ok(ctx, out.toString());
        return 1;
    }

    private static int help(final CommandContext<CommandSourceStack> ctx) {
        ok(ctx, "/welcome set <player> <title> | <subtitle>\n"
                + "/welcome off <player>\n"
                + "/welcome test <player>\n"
                + "/welcome list\n"
                + "Colours: &6 gold, &d pink, &c red, &b aqua, &l bold, &o italic.");
        return 1;
    }

    private static void ok(final CommandContext<CommandSourceStack> ctx, final String text) {
        ctx.getSource().m_288197_(() -> Component.m_237113_(text), false); // sendSuccess, not echoed to other ops
    }
}
