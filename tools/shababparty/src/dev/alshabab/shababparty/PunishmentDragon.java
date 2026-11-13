package dev.alshabab.shababparty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * An Ender Dragon guards Solo Leveling's Punishment Zone.
 *
 * <h2>Where</h2>
 * Skipping the Daily Quest sends a player to sololeveling:survival_dimension at their own overworld
 * X/Z, so two punished players can be thousands of blocks apart. A dragon cannot follow them: its
 * flight nodes (EnderDragon.findClosestNode) are always built around 0,0 of its dimension, and only
 * landing reads the fight origin. So arrivals are moved to one arena at 0,0, where the dragon's own
 * AI already wants to be. Leaving is untouched: Solo Leveling sends them home from the punX/punY/punZ
 * it saved before the trip, not from where they stand.
 *
 * <h2>Lifetime</h2>
 * Spawns a few seconds after someone is in the zone, vanishes (discard, no death, no drops) the moment
 * nobody is, and the next visit gets a new one at full health. Killed, it stays dead until the zone has
 * emptied once, so a kill buys peace for that visit only.
 *
 * <h2>Strength</h2>
 * It is a plain minecraft:ender_dragon, so {@link BossScaling} gives it the Ender Dragon's tier from
 * bossTiers (health and damage multiplier) like any other. A summoned dragon outside the End has no
 * boss bar of its own, so this class shows one.
 *
 * <h2>No rewards</h2>
 * The tag below keeps it out of {@link BossLevels}, {@link BossLoot} and {@link BossFanfare}, its drops
 * are cancelled, and the XP its death animation scatters (EnderDragon.tickDeath awards orbs directly,
 * bypassing LivingExperienceDropEvent) is stopped at the join event. A punishment must not be a farm.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PunishmentDragon {

    /** Entity tag marking our dragon. Saved in NBT, so a dragon left on disk is recognised later. */
    static final String TAG = "shababparty.punisher";

    // m_135785_ = ResourceKey.create, f_256858_ = Registries.DIMENSION.
    private static final ResourceKey<Level> ZONE = ResourceKey.m_135785_(
            Registries.f_256858_, new ResourceLocation("sololeveling", "survival_dimension"));

    private static final int SPAWN_DELAY_TICKS = 60;  // let arrivals land before it appears
    private static final int MOVE_DELAY_TICKS = 5;    // let Solo Leveling finish its own teleport first
    private static final int DRAGON_HEIGHT = 40;
    private static final int PLATFORM_RADIUS = 4;     // 9x9, used only where there is no dry ground

    private static UUID dragonId;
    private static boolean slain;
    private static int occupiedTicks;
    private static BlockPos arena;
    private static ServerBossEvent bar;
    private static final Map<UUID, Integer> ARRIVALS = new HashMap<>();

    private PunishmentDragon() {
    }

    public static boolean isPunisher(final Entity entity) {
        return entity.m_19880_().contains(TAG); // getTags
    }

    @SubscribeEvent
    public static void onChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (ShababParty.Config.PUNISHMENT_DRAGON_ENABLED.get() && ZONE.equals(event.getTo())) {
            ARRIVALS.put(event.getEntity().m_20148_(), MOVE_DELAY_TICKS);
        }
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ShababParty.Config.PUNISHMENT_DRAGON_ENABLED.get()) {
            return;
        }
        final MinecraftServer server = event.getServer();
        final ServerLevel zone = server.m_129880_(ZONE); // getLevel
        if (zone == null) {
            return;
        }

        moveArrivals(server, zone);

        final List<ServerPlayer> present = new ArrayList<>();
        for (final ServerPlayer p : zone.m_6907_()) { // players
            if (!p.m_5833_() && p.m_6084_()) { // not spectator, alive
                present.add(p);
            }
        }

        EnderDragon dragon = tracked(zone);
        if (present.isEmpty()) {
            if (dragon != null) {
                dragon.m_146870_(); // discard: gone, no death animation, no drops
            }
            reset();
            return;
        }

        occupiedTicks++;
        if (dragon == null && !slain && occupiedTicks >= SPAWN_DELAY_TICKS) {
            dragon = spawn(zone);
        }
        updateBar(present, dragon);
    }

    @SubscribeEvent
    public static void onDeath(final LivingDeathEvent event) {
        if (isPunisher(event.getEntity())) {
            slain = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrops(final LivingDropsEvent event) {
        if (isPunisher(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onJoin(final EntityJoinLevelEvent event) {
        if (event.getLevel().m_5776_()) {
            return;
        }
        final Entity entity = event.getEntity();
        if (entity instanceof ExperienceOrb) {
            // Orbs from the death animation. dragonId is cleared once the dragon is finally removed.
            if (slain && dragonId != null && ZONE.equals(event.getLevel().m_46472_())) {
                event.setCanceled(true);
            }
            return;
        }
        if (entity instanceof EnderDragon && isPunisher(entity) && !entity.m_20148_().equals(dragonId)) {
            // A dragon saved to disk by an earlier run (or a stray duplicate): it is not ours any more.
            event.setCanceled(true);
        }
    }

    private static EnderDragon tracked(final ServerLevel zone) {
        if (dragonId == null) {
            return null;
        }
        final Entity entity = zone.m_8791_(dragonId); // getEntity(UUID)
        if (entity instanceof EnderDragon dragon && !dragon.m_213877_()) { // isRemoved
            return dragon;
        }
        dragonId = null;
        return null;
    }

    private static EnderDragon spawn(final ServerLevel zone) {
        final EnderDragon dragon = EntityType.f_20565_.m_20615_(zone); // ENDER_DRAGON.create
        if (dragon == null) {
            return null;
        }
        final BlockPos a = arena(zone);
        dragon.m_7678_(a.m_123341_() + 0.5D, a.m_123342_() + DRAGON_HEIGHT, a.m_123343_() + 0.5D,
                ThreadLocalRandom.current().nextFloat() * 360.0F, 0.0F); // moveTo
        dragon.m_20049_(TAG); // addTag
        // A summoned dragon starts in HOVERING and just sits in the air. HOLDING_PATTERN is the
        // circling-and-attacking state the End fight runs in.
        dragon.m_31157_().m_31416_(EnderDragonPhase.f_31377_);
        dragonId = dragon.m_20148_();
        if (!zone.m_7967_(dragon)) { // addFreshEntity
            dragonId = null;
            return null;
        }
        return dragon;
    }

    /**
     * The arena: dry ground at 0,0, or a 9x9 obsidian floor if the column is water, lava or empty.
     * Obsidian is in minecraft:dragon_immune, so the dragon cannot fly through the floor. Worked out
     * once per visit, because the dragon carves up the terrain while it fights.
     */
    private static BlockPos arena(final ServerLevel zone) {
        if (arena != null) {
            return arena;
        }
        BlockPos top = zone.m_5452_(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.f_121853_); // getHeightmapPos
        final BlockPos ground = top.m_7495_(); // below
        final boolean dry = top.m_123342_() > zone.m_141937_() // above the floor of the world
                && !zone.m_8055_(ground).m_60795_()            // not air
                && zone.m_6425_(ground).m_76178_();            // not water or lava
        if (!dry) {
            final int y = Math.max(top.m_123342_(), zone.m_5736_() + 1) - 1; // at least sea level
            for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
                for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                    zone.m_46597_(new BlockPos(dx, y, dz), Blocks.f_50080_.m_49966_()); // obsidian
                }
            }
            top = new BlockPos(0, y + 1, 0);
        }
        arena = top;
        return arena;
    }

    private static void moveArrivals(final MinecraftServer server, final ServerLevel zone) {
        final Iterator<Map.Entry<UUID, Integer>> it = ARRIVALS.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, Integer> e = it.next();
            if (e.getValue() > 0) {
                e.setValue(e.getValue() - 1);
                continue;
            }
            it.remove();
            final ServerPlayer player = server.m_6846_().m_11259_(e.getKey()); // getPlayerList().getPlayer
            if (player == null || !ZONE.equals(player.m_9236_().m_46472_())) {
                continue;
            }
            final BlockPos a = arena(zone);
            final ThreadLocalRandom r = ThreadLocalRandom.current();
            final int x = a.m_123341_() + r.nextInt(-PLATFORM_RADIUS + 1, PLATFORM_RADIUS);
            final int z = a.m_123343_() + r.nextInt(-PLATFORM_RADIUS + 1, PLATFORM_RADIUS);
            final int y = Math.max(a.m_123342_(),
                    zone.m_5452_(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).m_123342_());
            player.m_8999_(zone, x + 0.5D, y, z + 0.5D, player.m_146908_(), player.m_146909_()); // teleportTo
        }
    }

    private static void updateBar(final List<ServerPlayer> present, final EnderDragon dragon) {
        if (dragon == null || !dragon.m_6084_()) { // none, or in its death animation
            if (bar != null) {
                bar.m_7706_(); // removeAllPlayers
            }
            return;
        }
        if (bar == null) {
            bar = new ServerBossEvent(Component.m_237115_("entity.minecraft.ender_dragon"),
                    BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        }
        bar.m_142711_(Math.max(0.0F, Math.min(1.0F, dragon.m_21223_() / dragon.m_21233_()))); // setProgress
        for (final ServerPlayer p : new ArrayList<>(bar.m_8324_())) {
            if (!present.contains(p)) {
                bar.m_6539_(p);
            }
        }
        for (final ServerPlayer p : present) {
            if (!bar.m_8324_().contains(p)) {
                bar.m_6543_(p);
            }
        }
    }

    private static void reset() {
        dragonId = null;
        slain = false;
        occupiedTicks = 0;
        arena = null;
        if (bar != null) {
            bar.m_7706_();
        }
    }
}
