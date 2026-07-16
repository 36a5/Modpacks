package dev.alshabab.colonyspeed.mixin;

import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.structurize.client.gui.AbstractWindowSkeleton;
import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import dev.alshabab.colonyspeed.ColonySpeed;
import dev.alshabab.colonyspeed.convert.Dropbox;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Adds the "Open Folder" button to the build tool, and converts any newly dropped schematic each time
 * the tool is opened.
 *
 * Converting here works without a restart because StructurePacks reads the pack's blueprints off disk
 * on every call rather than caching them - see ClientStructurePackLoaderMixin for the half of this
 * that does need to happen at load.
 *
 * The two-argument constructor delegates to this four-argument one, so injecting only here still runs
 * exactly once per window.
 */
@Mixin(WindowExtendedBuildTool.class)
public abstract class WindowExtendedBuildToolMixin {
    private static final String OPEN_FOLDER_ID = "colonyspeed_openfolder";

    @Inject(
            method = "<init>(Lnet/minecraft/core/BlockPos;ILjava/util/function/BiConsumer;Ljava/util/function/Predicate;)V",
            at = @At("RETURN"))
    private void colonyspeed$addOpenFolderButton(final CallbackInfo ci) {
        if (!ColonySpeed.Config.ENABLE_DROPBOX.get()) {
            return;
        }

        final AbstractWindowSkeleton self = (AbstractWindowSkeleton) (Object) this;

        // Pick up anything the player dropped in since the last time they opened this.
        for (final String line : Dropbox.convertAll()) {
            ColonySpeed.LOGGER.info(line);
        }

        final ButtonImage button = new ButtonImage();
        button.setID(OPEN_FOLDER_ID);
        button.setImage(new ResourceLocation("structurize", "textures/gui/buildtool/button_medium.png"), false);
        button.setSize(86, 17);
        // Directly under the stock "switch pack" button, which sits at 5,5 and is the same size.
        button.setPosition(5, 24);

        // Has to come after setSize, and cannot be skipped.
        //
        // A ButtonImage built in code starts with a text render box of 0x0, and ButtonImage.setSize
        // *scales* that box rather than setting it: textWidth = textWidth * newWidth / width. Zero
        // scaled by anything is still zero, so the label is laid out into a box with no width and
        // never draws - the button renders, and is clickable, and is simply blank. Buttons parsed
        // from XML never hit this because their constructor seeds the box from the pane params.
        button.setTextRenderBox(86, 17);
        button.setTextAlignment(Alignment.MIDDLE);

        // Component.literal(String), in SRG names like everything else Minecraft owns here.
        button.setText(Component.m_237113_("Open Folder"));
        button.setTextColor(0x000000);
        self.addChild(button);

        self.registerButton(OPEN_FOLDER_ID, colonyspeed$openFolder());
    }

    private static Runnable colonyspeed$openFolder() {
        return () -> {
            Dropbox.ensureFolders();
            // Util.getPlatform().openFile(File) - written in SRG names because this compiles against
            // the production jars. Going through Minecraft rather than java.awt.Desktop matters: on
            // macOS, Desktop.open from the render thread deadlocks against LWJGL.
            Util.m_137581_().m_137644_(Dropbox.dropFolder().toFile());
        };
    }
}
