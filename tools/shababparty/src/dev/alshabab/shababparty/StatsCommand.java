package dev.alshabab.shababparty;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.alshabab.shababparty.mixin.PlayerListAccessor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /stat get|set|add <player> <field> [value] - edit the six numbers the Discord leaderboard ranks by,
 * for online and offline players, while the server is running.
 *
 * Why this exists: those numbers come from world/stats/<uuid>.json, and that file cannot be edited on
 * a live server. The server holds each player's stats in memory (ServerStatsCounter) and rewrites the
 * file from memory on autosave and on logout, so a hand edit is silently overwritten. The old fix was
 * server/set-player-deaths.ps1, which refused to run unless the server was stopped and covered exactly
 * one of the six columns.
 *
 * So we edit the counter the server itself flushes, via PlayerListAccessor, then save() it. The next
 * autosave writes our numbers back out instead of reverting them, and the Discord bot - which keeps
 * reading the same JSON - needs no change.
 *
 * blocks_mined and blocks_placed are not single fields: the JSON stores one entry per block type
 * (80 and 97 of them on a real player) and the board shows the sum. See setSum().
 *
 * SRG names throughout, because shababparty compiles against the production jars with no
 * reobfuscation step. Each one is commented where it is used.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StatsCommand {

    /** The six columns of the !leaderboard board, in the order they are documented. */
    public static final List<String> FIELDS = List.of(
            "deaths", "mob_kills", "player_kills", "play_time", "blocks_mined", "blocks_placed");

    /** Where the delta lands when you set one of the two sum fields. Ordinary blocks, so an inflated
     *  entry never looks anomalous. */
    private static final ResourceLocation MINED_SINK = new ResourceLocation("minecraft", "stone");
    private static final ResourceLocation PLACED_SINK = new ResourceLocation("minecraft", "dirt");

    private static final SimpleCommandExceptionType UNKNOWN_PLAYER =
            new SimpleCommandExceptionType(Component.m_237113_("No player by that name has ever joined this server."));
    private static final SimpleCommandExceptionType UNKNOWN_FIELD =
            new SimpleCommandExceptionType(Component.m_237113_("Unknown field. Valid: " + String.join(", ", FIELDS)));

    private StatsCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        // m_82127_ = literal, m_82129_ = argument, m_6761_ = hasPermission.
        // Level 3 is the op level /slr already needs, so anyone who can run that can run this.
        event.getDispatcher().register(
                Commands.m_82127_("stat")
                        .requires(src -> src.m_6761_(3))
                        .then(Commands.m_82127_("get")
                                .then(Commands.m_82129_("player", StringArgumentType.word())
                                        .then(Commands.m_82129_("field", StringArgumentType.word())
                                                .suggests(StatsCommand::suggestFields)
                                                .executes(StatsCommand::get)))));
    }

    // Pure Brigadier - no Minecraft symbol, so nothing to remap.
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestFields(
            final CommandContext<CommandSourceStack> ctx,
            final com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        final String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (final String f : FIELDS) {
            if (f.startsWith(prefix)) {
                builder.suggest(f);
            }
        }
        return builder.buildFuture();
    }

    private static int get(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack src = ctx.getSource();
        final MinecraftServer server = src.m_81377_();          // getServer
        final String name = StringArgumentType.getString(ctx, "player");
        final String field = field(ctx);

        final ServerStatsCounter counter = counterFor(server, name);
        final int value = read(counter, field);

        // m_288197_ = sendSuccess, m_237113_ = Component.literal. false = do not echo to other ops.
        src.m_288197_(() -> Component.m_237113_(name + " " + field + " = " + value + suffix(field, value)), false);
        return value;
    }

    /** play_time is stored in ticks, which is unreadable. Echo the hours so a wrong order of
     *  magnitude is obvious at a glance. */
    private static String suffix(final String field, final int value) {
        return field.equals("play_time") ? String.format(" (%.1f hours)", value / 72000.0D) : "";
    }

    private static String field(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final String field = StringArgumentType.getString(ctx, "field").toLowerCase(java.util.Locale.ROOT);
        if (!FIELDS.contains(field)) {
            throw UNKNOWN_FIELD.create();
        }
        return field;
    }

    // ---- player resolution -------------------------------------------------------------------

    /** The live player, or null if they are not online. m_6846_ = getPlayerList, m_11255_ = getPlayerByName. */
    static ServerPlayer onlineOrNull(final MinecraftServer server, final String name) {
        return server.m_6846_().m_11255_(name);
    }

    /** Online players resolve from the live entity; offline ones from the profile cache, which is
     *  usercache.json - the same file set-player-deaths.ps1 used to read. */
    static UUID resolve(final MinecraftServer server, final String name) throws CommandSyntaxException {
        final ServerPlayer online = onlineOrNull(server, name);
        if (online != null) {
            return online.m_20148_();                            // getUUID
        }
        // m_129927_ = getProfileCache, m_10996_ = get(String). GameProfile is authlib: readable.
        final Optional<GameProfile> profile = server.m_129927_().m_10996_(name);
        if (profile.isEmpty()) {
            throw UNKNOWN_PLAYER.create();
        }
        return profile.get().getId();
    }

    /**
     * The ServerStatsCounter the server itself will flush for this player.
     *
     * Online: ask PlayerList for it (m_11239_ = getPlayerStats), which is the canonical getter and
     * always returns the live in-memory counter.
     *
     * Offline: there is no entry in PlayerList's map, so build one from the player's JSON and PUT IT
     * IN THE MAP. That last part is the point - the server now owns this counter, so when it next
     * saves it writes our edit rather than reverting it.
     */
    static ServerStatsCounter counterFor(final MinecraftServer server, final String name) throws CommandSyntaxException {
        final ServerPlayer online = onlineOrNull(server, name);
        if (online != null) {
            return server.m_6846_().m_11239_(online);
        }

        final UUID uuid = resolve(server, name);
        final Map<UUID, ServerStatsCounter> stats = ((PlayerListAccessor) server.m_6846_()).shababparty$stats();
        final ServerStatsCounter cached = stats.get(uuid);
        if (cached != null) {
            // They logged out this session; the server still holds their counter and will flush it.
            return cached;
        }

        // m_129843_ = getWorldPath, f_78175_ = LevelResource.PLAYER_STATS_DIR ("stats").
        final File dir = server.m_129843_(LevelResource.f_78175_).toFile();
        final ServerStatsCounter loaded = new ServerStatsCounter(server, new File(dir, uuid + ".json"));
        stats.put(uuid, loaded);
        return loaded;
    }

    // ---- reading -----------------------------------------------------------------------------

    /** m_13015_ = StatsCounter.getValue(Stat). f_12988_ = Stats.CUSTOM, m_12902_ = StatType.get. */
    private static int custom(final ServerStatsCounter counter, final ResourceLocation id) {
        return counter.m_13015_(Stats.f_12988_.m_12902_(id));
    }

    /**
     * StatType implements Iterable<Stat<T>>, so this walks every block (or item) the game knows,
     * modded ones included - which is exactly what the Discord bot does when it sums every key under
     * "minecraft:mined" whatever its namespace. Absent entries read 0, so the sum matches the file.
     */
    private static int sumMined(final ServerStatsCounter counter) {
        int total = 0;
        for (final Stat<Block> stat : Stats.f_12949_) {          // Stats.BLOCK_MINED
            total += counter.m_13015_(stat);
        }
        return total;
    }

    private static int sumPlaced(final ServerStatsCounter counter) {
        int total = 0;
        for (final Stat<Item> stat : Stats.f_12982_) {           // Stats.ITEM_USED
            total += counter.m_13015_(stat);
        }
        return total;
    }

    static int read(final ServerStatsCounter counter, final String field) {
        switch (field) {
            case "deaths":        return custom(counter, Stats.f_12935_);
            case "mob_kills":     return custom(counter, Stats.f_12936_);
            case "player_kills":  return custom(counter, Stats.f_12938_);
            case "play_time":     return custom(counter, Stats.f_144255_);
            case "blocks_mined":  return sumMined(counter);
            case "blocks_placed": return sumPlaced(counter);
            default: throw new IllegalArgumentException(field); // field() already rejected these
        }
    }
}
