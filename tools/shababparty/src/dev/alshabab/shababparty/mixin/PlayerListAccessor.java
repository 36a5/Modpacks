package dev.alshabab.shababparty.mixin;

import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * Exposes PlayerList's private uuid -> ServerStatsCounter map.
 *
 * This map is the whole reason /stat can work on a live server. The server keeps one
 * ServerStatsCounter per player in memory and writes world/stats/<uuid>.json *from it* on autosave
 * and on logout - so editing that file by hand is pointless, the server just overwrites it. Editing
 * the counter in this map instead means the server's next flush writes our numbers back out.
 *
 * For an offline player the map has no entry. StatsCommand builds one from disk and puts it here,
 * so the server adopts it as its own and cannot later revert the edit.
 *
 * Written against SRG names because shababparty compiles straight against the production jars with
 * no reobfuscation step: f_11202_ = the stats map. (f_11197_ is players-by-uuid and f_11203_ is
 * advancements; picking the wrong one would compile and then silently corrupt the wrong thing.)
 */
@Mixin(PlayerList.class)
public interface PlayerListAccessor {
    @Accessor("f_11202_")
    Map<UUID, ServerStatsCounter> shababparty$stats();
}
