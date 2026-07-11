package dev.alshabab.shababparty.mixin;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbteams.api.client.KnownClientPlayer;
import dev.ftb.mods.ftbteams.data.ClientTeamManagerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * FTB Teams figures out "which known player am I?" using the UUID the *launcher* handed the client
 * (Minecraft.getInstance().getUser().getGameProfile().getId()). On an online-mode server that is the
 * same UUID the server assigns, so the lookup hits.
 *
 * An offline-mode server instead assigns UUID.nameUUIDFromBytes("OfflinePlayer:" + name), and cracked
 * launchers hand the client an unrelated UUID -- TLauncher issues a v1 time-based one. The lookup then
 * misses, selfKnownPlayer stays null, and the team GUI degrades to "Team data has not been received
 * from the server".
 *
 * The launcher's UUID never crosses the wire, so the server cannot compensate; this has to be fixed on
 * the client. When the launcher UUID is absent from the synced player map, retry with the offline-mode
 * UUID derived from the player's name -- which is precisely the key the server used. Online-mode
 * behaviour is untouched, because there the first lookup already succeeds.
 */
@Mixin(ClientTeamManagerImpl.class)
public abstract class ClientTeamManagerImplMixin {

    @Shadow @Final private Map<UUID, KnownClientPlayer> knownPlayers;

    @Redirect(
            method = "initSelfDetails",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/authlib/GameProfile;getId()Ljava/util/UUID;"
            )
    )
    private UUID shababparty$resolveSelfId(GameProfile localProfile) {
        UUID launcherId = localProfile.getId();
        if (launcherId != null && knownPlayers.containsKey(launcherId)) {
            return launcherId;
        }

        String name = localProfile.getName();
        if (name != null && !name.isEmpty()) {
            UUID offlineId = UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            if (knownPlayers.containsKey(offlineId)) {
                return offlineId;
            }
        }

        return launcherId;
    }
}
