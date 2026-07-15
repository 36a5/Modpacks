package dev.alshabab.shababparty;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.solocraft.network.SololevelingModVariables;
import net.solocraft.procedures.JobAdvPointGainProcedure;
import net.solocraft.procedures.XPGainProcedure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Two things, both hanging off FTB Teams as the one source of truth for who is in a party:
 *
 *   1. The bridge. Solo Leveling decides whether two players can hurt each other by comparing a
 *      plain String field on each player ("party"); if they match, it cancels the attack. It never
 *      hears about FTB Teams. So once a second we copy each player's FTB party into that field, and
 *      friendly fire between party members stops as a consequence. Empty string means "no party",
 *      which is the sentinel Solo Leveling itself already special-cases.
 *
 *   2. The reward share. Solo Leveling pays hunter XP and job advancement points to whoever landed
 *      the killing blow and to nobody else. We pay the killer's party mates by calling Solo
 *      Leveling's own routines once per member, rather than reimplementing their formulas -- so the
 *      mob reward table, each member's personal multiplier, and the soloDungeonProgressionOnly /
 *      soloLevelingXPMultiplier gamerules all keep applying, inside dungeon portals and out, and
 *      stay correct if the mod updates its numbers.
 *
 *      This is Solo Leveling's own levelling track (PlayerVariables.Xp -> Level -> rank), not
 *      vanilla experience orbs, which are left alone.
 *
 *      Item drops need nothing from us: Solo Leveling spawns them as ItemEntities on the ground at
 *      the corpse, so they are already there for whoever walks over them.
 *
 * Minecraft methods are called by their SRG names (m_9236_ and friends) because this mod is compiled
 * against the production-mapped jar with plain javac rather than through ForgeGradle, so there is no
 * reobfuscation step to translate readable names for us. Each one is annotated with what it is.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PartySupport {

    /** Solo Leveling's own "not in a party" value; it skips the friendly-fire check on this. */
    private static final String NO_PARTY = "";

    private static final int RECONCILE_INTERVAL_TICKS = 20;

    private static int ticksSinceReconcile;

    private PartySupport() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!ShababParty.Config.PARTY_BRIDGE_ENABLED.get()) {
            return;
        }
        if (++ticksSinceReconcile < RECONCILE_INTERVAL_TICKS) {
            return;
        }
        ticksSinceReconcile = 0;

        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }

        for (ServerPlayer player : event.getServer().m_6846_().m_11314_()) { // getPlayerList().getPlayers()
            syncPartyField(player);
        }
    }

    private static void syncPartyField(ServerPlayer player) {
        SololevelingModVariables.PlayerVariables vars = capabilityOf(player);
        if (vars == null) {
            return;
        }

        String current = vars.party == null ? NO_PARTY : vars.party;
        String desired = partyKeyOf(player.m_20148_()); // getUUID()
        if (current.equals(desired)) {
            return;
        }

        vars.party = desired;
        vars.syncPlayerVariables(player);
    }

    /**
     * Forge patches getCapability onto Entity at runtime, but it does that by binary-patching the
     * Minecraft jar as the game boots -- there is no patched jar on disk for javac to compile
     * against, so Entity has no such method at compile time. Going through the interface Entity
     * actually implements at runtime gets us the same call without needing the patched jar.
     */
    private static SololevelingModVariables.PlayerVariables capabilityOf(ServerPlayer player) {
        return ((ICapabilityProvider) player)
                .getCapability(SololevelingModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                .orElse(null);
    }

    /**
     * Every player has an FTB team, but a solo player's is a private one-man "player team" rather
     * than a party. Only a real party counts, otherwise two unpartied players would end up sharing a
     * party key and be unable to hit each other.
     */
    private static String partyKeyOf(UUID playerId) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerId)
                .filter(Team::isPartyTeam)
                .map(team -> team.getId().toString())
                .orElse(NO_PARTY);
    }

    /**
     * Runs at LOWEST so Solo Leveling has already paid the killer by the time we pay the party. We
     * do not actually depend on that ordering -- we never read the killer's XP -- but it keeps the
     * XP popups in a sensible order for the player.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        boolean shareXp = ShababParty.Config.XP_SHARE_ENABLED.get();
        boolean shareJobPoints = ShababParty.Config.JOB_POINT_SHARE_ENABLED.get();
        if (!shareXp && !shareJobPoints) {
            return;
        }

        LivingEntity dead = event.getEntity();
        Level level = dead.m_9236_(); // level()
        if (level.m_5776_()) { // isClientSide()
            return;
        }

        Player killer = resolveXpEarner(event.getSource());
        if (killer == null) {
            return;
        }

        MinecraftServer server = level.m_7654_(); // getServer()
        if (server == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }

        for (ServerPlayer member : partyRecipients(killer, dead, level)) {
            if (member.m_20148_().equals(killer.m_20148_())) {
                continue; // Solo Leveling already paid the killer; do not double-pay
            }

            // Solo Leveling's own reward routines, as if this member had landed the kill. Both of
            // them re-check that the member has actually awakened as a hunter, so an unawakened
            // party member still earns nothing -- exactly as if they had made the kill themselves.
            if (shareXp) {
                XPGainProcedure.execute(level, dead, member);
            }
            if (shareJobPoints) {
                JobAdvPointGainProcedure.execute(dead, member);
            }
        }
    }

    /**
     * The killer's party members within xpShareRadius of the kill, in the same dimension, online --
     * INCLUDING the killer. The one place party-and-range resolution lives, shared by the XP/job-point
     * share here and by {@link BossLevels}. An empty list if the killer is not in a real party.
     */
    static List<ServerPlayer> partyRecipients(Player killer, LivingEntity dead, Level level) {
        MinecraftServer server = level.m_7654_(); // getServer()
        if (server == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return List.of();
        }
        UUID killerId = killer.m_20148_(); // getUUID()
        Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(killerId)
                .filter(Team::isPartyTeam);
        if (team.isEmpty()) {
            return List.of();
        }

        double radius = ShababParty.Config.XP_SHARE_RADIUS.get();
        double radiusSq = radius <= 0.0D ? -1.0D : radius * radius;

        List<ServerPlayer> recipients = new ArrayList<>();
        for (UUID memberId : team.get().getMembers()) {
            ServerPlayer member = server.m_6846_().m_11259_(memberId); // getPlayerList().getPlayer(uuid)
            if (member == null) {
                continue; // offline
            }
            if (member.m_9236_() != level) { // level() -- same Level instance means same dimension
                continue;
            }
            if (radiusSq >= 0.0D && member.m_20280_(dead) > radiusSq) { // distanceToSqr(entity)
                continue;
            }
            recipients.add(member);
        }
        return recipients;
    }

    /**
     * Who Solo Leveling considers to have earned the kill: the player, or the owner of a tamed pet
     * that made the kill. Mirrors XPGainProcedure so we never pay a party that Solo Leveling itself
     * would not have paid.
     */
    static Player resolveXpEarner(DamageSource source) {
        if (source == null) {
            return null;
        }

        Entity attacker = source.m_7639_(); // getEntity()
        if (attacker instanceof Player player) {
            return player;
        }
        if (attacker instanceof TamableAnimal pet
                && pet.m_21824_() // isTame()
                && pet.m_269323_() instanceof Player owner) { // getOwner()
            return owner;
        }
        return null;
    }
}
