package dev.alshabab.colonyspeed.mixin;

import dev.alshabab.colonyspeed.ColonySpeed;
import dev.alshabab.colonyspeed.convert.Dropbox;
import com.ldtteam.structurize.storage.ClientStructurePackLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gets the generated pack onto disk before Structurize goes looking for packs.
 *
 * Structurize registers packs exactly once, at client load. A pack folder that appears afterwards is
 * invisible until the next restart. So the folders (and any file the player dropped in while the game
 * was shut) have to be dealt with at the head of this method, not after it.
 *
 * Individual blueprints are a different story - StructurePacks reads categories and blueprints off
 * disk with Files.list on every call, so a .blueprint written into an already-registered pack shows
 * up immediately. That is what lets the build tool convert on open without a restart.
 */
@Mixin(ClientStructurePackLoader.class)
public abstract class ClientStructurePackLoaderMixin {
    @Inject(method = "onClientLoading", at = @At("HEAD"))
    private static void colonyspeed$prepareDropbox(final CallbackInfo ci) {
        if (!ColonySpeed.Config.ENABLE_DROPBOX.get()) {
            return;
        }
        Dropbox.ensureFolders();
        Dropbox.convertAll();
    }
}
