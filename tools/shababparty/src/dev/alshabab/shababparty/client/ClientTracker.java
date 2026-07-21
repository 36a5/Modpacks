package dev.alshabab.shababparty.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.alshabab.shababparty.ShababParty;
import dev.alshabab.shababparty.network.TrackUpdatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The tracker's side of the /track feature: it takes the target's position from the server and shows
 * it two ways at once -- a through-wall ESP box in the world and a live waypoint on Xaero's Minimap
 * (via {@link XaeroWaypointBridge}).
 *
 * <h2>Why the box is drawn in the HUD pass</h2>
 * Exactly the reasons documented on {@link ClientDamageNumbers}: drawing in GUI space after the whole
 * vanilla HUD makes the box immune to terrain occlusion (so it is visible through walls, which is the
 * point of an ESP), immune to the Oculus shader pack reordering the level render, and a constant
 * readable size. The target's world-space bounding box is projected to screen coordinates and drawn
 * as a flat outline -- classic 2D box ESP.
 *
 * <h2>Cross-dimension</h2>
 * A box and a minimap waypoint only make sense while the target shares the tracker's dimension. When
 * they are apart, the box and waypoint are dropped and a small line of HUD text names the dimension
 * and coordinates the target was last reported at instead.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, value = Dist.CLIENT)
public final class ClientTracker {

    private static final int COLOR_ACTIVE = 0xFFFF5555;   // red
    private static final int COLOR_OFFLINE = 0xFFAAAAAA;  // grey
    private static final int EDGE_THICKNESS = 2;

    /** Player collision half-width and height, used to size the box from raw coordinates. */
    private static final double HALF_WIDTH = 0.3D;
    private static final double HEIGHT = 1.8D;

    /**
     * If the server goes this many client ticks without an update, assume tracking died without a
     * clean STATE_CLEARED (e.g. the tracker's channel dropped) and tear the overlay down. The server
     * sends every 4 ticks, so 60 is generous.
     */
    private static final int STALE_TICKS = 60;

    // Current target state, all mutated on the client thread.
    private static int state = TrackUpdatePacket.STATE_CLEARED;
    private static String name = "";
    private static String dimension = "";
    private static double x;
    private static double y;
    private static double z;
    private static int entityId = -1;
    private static int ticksSinceUpdate;

    // View state captured in the level render and consumed in the HUD render. Same handoff as
    // ClientDamageNumbers.
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static double camX;
    private static double camY;
    private static double camZ;
    private static boolean viewReady;

    private ClientTracker() {
    }

    /* ------------------------------------------------------------------ receiving */

    public static void accept(final TrackUpdatePacket p) {
        state = p.state;
        ticksSinceUpdate = 0;

        if (p.state == TrackUpdatePacket.STATE_CLEARED) {
            reset();
            return;
        }

        name = p.name;
        entityId = p.entityId;
        if (p.state == TrackUpdatePacket.STATE_ACTIVE) {
            // OFFLINE keeps the last known position; ACTIVE refreshes it.
            dimension = p.dimension;
            x = p.x;
            y = p.y;
            z = p.z;
        }

        // Feed Xaero only when the target is in our dimension; its coordinates are per-dimension.
        if (p.state == TrackUpdatePacket.STATE_ACTIVE && sameDimension()) {
            XaeroWaypointBridge.update(name,
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        } else {
            XaeroWaypointBridge.clear();
        }
    }

    private static void reset() {
        state = TrackUpdatePacket.STATE_CLEARED;
        name = "";
        dimension = "";
        entityId = -1;
        XaeroWaypointBridge.clear();
    }

    private static boolean hasTarget() {
        return state != TrackUpdatePacket.STATE_CLEARED;
    }

    private static boolean sameDimension() {
        final ClientLevel level = Minecraft.m_91087_().f_91073_;
        if (level == null) {
            return false;
        }
        return level.m_46472_().m_135782_().toString().equals(dimension);
    }

    /* --------------------------------------------------------------------- ticking */

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !hasTarget()) {
            return;
        }
        if (++ticksSinceUpdate > STALE_TICKS) {
            reset();
        }
    }

    /* ------------------------------------------------------ capturing the view matrix */

    @SubscribeEvent
    public static void onRenderLevel(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        final Vec3 cam = event.getCamera().m_90583_();
        camX = cam.f_82479_;
        camY = cam.f_82480_;
        camZ = cam.f_82481_;
        VIEW_PROJ.set(event.getProjectionMatrix()).mul(event.getPoseStack().m_85850_().m_252922_());
        viewReady = true;
    }

    /* ---------------------------------------------------------------------- drawing */

    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post event) {
        if (!hasTarget() || !viewReady) {
            return;
        }

        final Minecraft mc = Minecraft.m_91087_();
        final Font font = mc.f_91062_;
        final Window window = event.getWindow();
        final int screenW = window.m_85445_();
        final int screenH = window.m_85446_();
        final GuiGraphics g = event.getGuiGraphics();

        if (!sameDimension()) {
            drawCrossDimension(g, font, screenW);
            return;
        }

        // Prefer the live entity when it is loaded -- its interpolated position is smoother than the
        // five-per-second packet. Fall back to the reported coordinates otherwise.
        double px = x;
        double py = y;
        double pz = z;
        final ClientLevel level = mc.f_91073_;
        if (entityId >= 0 && level != null) {
            final Entity e = level.m_6815_(entityId);
            if (e != null) {
                px = e.m_20185_();
                py = e.m_20186_();
                pz = e.m_20189_();
            }
        }

        drawBox(g, font, screenW, screenH, px, py, pz);
    }

    private static void drawBox(final GuiGraphics g, final Font font, final int screenW,
                                final int screenH, final double px, final double py, final double pz) {
        // Project the eight corners of the target's bounding box to the screen. If any corner is
        // behind the camera the projection is unreliable, so the box is skipped that frame.
        float minSx = Float.MAX_VALUE;
        float minSy = Float.MAX_VALUE;
        float maxSx = -Float.MAX_VALUE;
        float maxSy = -Float.MAX_VALUE;

        for (int i = 0; i < 8; i++) {
            final double cx = px + ((i & 1) == 0 ? -HALF_WIDTH : HALF_WIDTH);
            final double cy = py + ((i & 2) == 0 ? 0.0D : HEIGHT);
            final double cz = pz + ((i & 4) == 0 ? -HALF_WIDTH : HALF_WIDTH);

            final Vector4f clip = new Vector4f(
                    (float) (cx - camX), (float) (cy - camY), (float) (cz - camZ), 1.0F);
            clip.mul(VIEW_PROJ);
            if (clip.w <= 0.0F) {
                return; // corner behind the camera
            }
            final float sx = (clip.x / clip.w * 0.5F + 0.5F) * screenW;
            final float sy = (1.0F - (clip.y / clip.w * 0.5F + 0.5F)) * screenH;
            minSx = Math.min(minSx, sx);
            minSy = Math.min(minSy, sy);
            maxSx = Math.max(maxSx, sx);
            maxSy = Math.max(maxSy, sy);
        }

        // Clamp well off-screen coordinates so a target right next to the camera does not ask the GUI
        // to fill a rectangle millions of pixels wide.
        final int margin = 64;
        int x1 = (int) clamp(minSx, -margin, screenW + margin);
        int y1 = (int) clamp(minSy, -margin, screenH + margin);
        int x2 = (int) clamp(maxSx, -margin, screenW + margin);
        int y2 = (int) clamp(maxSy, -margin, screenH + margin);
        if (x2 - x1 < 1 || y2 - y1 < 1) {
            return;
        }

        final int color = state == TrackUpdatePacket.STATE_OFFLINE ? COLOR_OFFLINE : COLOR_ACTIVE;

        // Four edges of a flat outline. m_280509_ = fill(x1, y1, x2, y2, argb).
        g.m_280509_(x1, y1, x2, y1 + EDGE_THICKNESS, color);          // top
        g.m_280509_(x1, y2 - EDGE_THICKNESS, x2, y2, color);          // bottom
        g.m_280509_(x1, y1, x1 + EDGE_THICKNESS, y2, color);          // left
        g.m_280509_(x2 - EDGE_THICKNESS, y1, x2, y2, color);          // right

        final double dist = Math.sqrt(sq(px - camX) + sq(py - camY) + sq(pz - camZ));
        final String tag = state == TrackUpdatePacket.STATE_OFFLINE
                ? name + " (offline)"
                : name + "  " + (int) dist + "m";
        final String coords = "(" + (int) Math.floor(px) + ", "
                + (int) Math.floor(py) + ", " + (int) Math.floor(pz) + ")";

        final int cx = (x1 + x2) / 2;
        drawCentered(g, font, tag, cx, y1 - 2 - font.f_92710_ * 2, color);
        drawCentered(g, font, coords, cx, y1 - 2 - font.f_92710_, color);
    }

    private static void drawCrossDimension(final GuiGraphics g, final Font font, final int screenW) {
        final String dim = prettyDimension(dimension);
        final String line = state == TrackUpdatePacket.STATE_OFFLINE
                ? name + " is offline -- last seen in " + dim
                : name + " is in " + dim + "  (" + (int) Math.floor(x) + ", "
                        + (int) Math.floor(y) + ", " + (int) Math.floor(z) + ")";
        drawCentered(g, font, line, screenW / 2, 4, COLOR_OFFLINE);
    }

    /* ------------------------------------------------------------------------ helpers */

    private static void drawCentered(final GuiGraphics g, final Font font, final String text,
                                     final int cx, final int y, final int argb) {
        final PoseStack pose = g.m_280168_();
        pose.m_85836_();
        // m_280056_ = drawString(font, text, x, y, argb, dropShadow).
        g.m_280056_(font, text, cx - font.m_92895_(text) / 2, y, argb, true);
        pose.m_85849_();
    }

    private static String prettyDimension(final String id) {
        final int colon = id.indexOf(':');
        final String path = colon >= 0 ? id.substring(colon + 1) : id;
        return path.replace('_', ' ');
    }

    private static double clamp(final double v, final double lo, final double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static double sq(final double v) {
        return v * v;
    }

    /** Leaving a world drops any target so it does not bleed into the next one. */
    @SubscribeEvent
    public static void onLoggedOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        viewReady = false;
    }
}
