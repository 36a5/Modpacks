package dev.alshabab.shababparty.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.alshabab.shababparty.ShababParty;
import dev.alshabab.shababparty.network.DamageNumberPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Floating damage numbers, drawn as a HUD overlay rather than as world geometry.
 *
 * <h2>Why the HUD and not the world</h2>
 * The first version drew billboarded text during the level render. Three things were wrong with
 * that, and they all have the same fix:
 *
 * <ul>
 *   <li><b>Occlusion.</b> Depth-tested text is hidden by the mob you just hit. Switching to
 *       SEE_THROUGH helped, but nothing drawn inside the level render can ever appear over the
 *       inventory, the hotbar, or any other UI.</li>
 *   <li><b>Shaders.</b> This pack ships Oculus. Shader packs restructure the level render and
 *       reorder its stages, so text emitted mid-level is at their mercy.</li>
 *   <li><b>Size.</b> World-space text shrinks with distance. On a large boss the anchor point is
 *       far away and the number became unreadable exactly when it mattered most.</li>
 * </ul>
 *
 * <p>So the world position is projected to screen coordinates and the text is drawn in GUI space,
 * after the whole vanilla HUD. It is always on top of everything, always the same readable size,
 * and completely outside whatever the shader pack is doing.
 *
 * <h2>One popup per target, not one per hit</h2>
 * Hits used to spawn a popup each. Cataclysm's Meat Shredder ignores invincibility frames and Epic
 * Fight combos land several hits per swing, so numbers piled onto the same few pixels and turned
 * into unreadable mush. Now each target owns one popup that updates in place and accumulates a
 * combo total, which is both legible and more useful: {@code 150 (450) x3} says this hit did 150,
 * the combo has done 450, over three hits.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, value = Dist.CLIENT)
public final class ClientDamageNumbers {

    /** Bold, via the legacy formatting code. Cheaper than styling a Component for one flag. */
    private static final String BOLD = "§l";

    private static final Map<Integer, Popup> POPUPS = new LinkedHashMap<>();

    /**
     * View state captured during the level render, where the camera and projection are known, and
     * consumed during the HUD render, where they are not.
     */
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static double camX;
    private static double camY;
    private static double camZ;
    private static boolean viewReady;

    private ClientDamageNumbers() {
    }

    private static final class Popup {
        private double x;
        private double y;
        private double z;
        private final int rgb;
        private float lastHit;
        private float total;
        private int hits;
        private int age;

        private Popup(final double x, final double y, final double z,
                      final int rgb, final float damage) {
            this.rgb = rgb;
            moveTo(x, y, z);
            add(damage);
        }

        private void moveTo(final double nx, final double ny, final double nz) {
            this.x = nx;
            this.y = ny;
            this.z = nz;
        }

        private void add(final float damage) {
            this.lastHit = damage;
            this.total += damage;
            this.hits++;
            this.age = 0;
        }
    }

    /* ------------------------------------------------------------------ receiving */

    public static void accept(final DamageNumberPacket p) {
        if (!ClientConfig.ENABLED.get() || !ClientConfig.bucketEnabled(p.bucket)) {
            return;
        }

        final Minecraft mc = Minecraft.m_91087_();
        final ClientLevel level = mc.f_91073_;
        if (level == null) {
            return;
        }

        // Out of render distance, or already removed. Normal, not an error.
        final Entity victim = level.m_6815_(p.entityId);
        if (victim == null) {
            return;
        }

        final float damage = ClientConfig.SHOW_RAW.get() ? p.raw : p.finalAmount;
        final Vec3 at = anchor(mc, victim);

        // Key on target *and* bucket: a number for damage you dealt must never merge its combo into
        // a number for damage you took, even in the odd case where both concern the same entity.
        final int key = p.entityId * 4 + p.bucket;

        final Popup existing = POPUPS.get(key);
        if (existing != null) {
            // Still on screen, so this hit belongs to the running combo.
            existing.moveTo(at.f_82479_, at.f_82480_, at.f_82481_);
            existing.add(damage);
            return;
        }

        POPUPS.put(key, new Popup(at.f_82479_, at.f_82480_, at.f_82481_,
                ClientConfig.colorOf(p.bucket), damage));

        // Without a cap, a long fight in a crowd grows this map without bound.
        final int cap = ClientConfig.MAX_POPUPS.get();
        final Iterator<Integer> it = POPUPS.keySet().iterator();
        while (POPUPS.size() > cap && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    /**
     * Where the number sits in the world.
     *
     * <p>Mid-body, not the eyes. On something the size of a Cataclysm boss the eye position is metres
     * above the part of the model you are actually looking at, which put the number off in the
     * corner of the screen while the fight happened somewhere else.
     *
     * <p>For damage you take the victim is you, and your own mid-body is behind the camera in first
     * person, so that anchors a short way along your view instead.
     */
    private static Vec3 anchor(final Minecraft mc, final Entity victim) {
        if (victim == mc.f_91074_) {
            return victim.m_146892_()
                    .m_82549_(victim.m_20154_().m_82490_(SELF_FORWARD))
                    .m_82492_(0.0D, SELF_DROP, 0.0D);
        }
        return new Vec3(
                victim.m_20185_(),
                victim.m_20186_() + victim.m_20206_() * 0.5F,
                victim.m_20189_());
    }

    /** How far along your view a damage-taken number is anchored. */
    private static final double SELF_FORWARD = 1.6D;

    /** How far below eye level that number sits, so it clears the crosshair. */
    private static final double SELF_DROP = 0.45D;

    /* --------------------------------------------------------------------- ticking */

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || POPUPS.isEmpty()) {
            return;
        }
        final int lifetime = ClientConfig.LIFETIME_TICKS.get();
        POPUPS.values().removeIf(p -> ++p.age >= lifetime);
    }

    /* ------------------------------------------------------ capturing the view matrix */

    /**
     * Draws nothing. The camera and projection are only available inside the level render, and the
     * text is drawn later in the HUD pass, so they are stashed here.
     */
    @SubscribeEvent
    public static void onRenderLevel(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        final Vec3 cam = event.getCamera().m_90583_();
        camX = cam.f_82479_;
        camY = cam.f_82480_;
        camZ = cam.f_82481_;
        // clip = projection * modelview. The PoseStack here carries the camera rotation but not its
        // translation, which is why positions are made camera-relative before transforming.
        VIEW_PROJ.set(event.getProjectionMatrix()).mul(event.getPoseStack().m_85850_().m_252922_());
        viewReady = true;
    }

    /* ---------------------------------------------------------------------- drawing */

    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post event) {
        if (POPUPS.isEmpty() || !viewReady || !ClientConfig.ENABLED.get()) {
            return;
        }

        final Minecraft mc = Minecraft.m_91087_();
        final Font font = mc.f_91062_;
        final Window window = event.getWindow();
        final int screenW = window.m_85445_();
        final int screenH = window.m_85446_();

        final GuiGraphics g = event.getGuiGraphics();
        final PoseStack pose = g.m_280168_();

        final float partial = event.getPartialTick();
        final float scale = (float) (double) ClientConfig.SCALE.get();
        final float rise = (float) (double) ClientConfig.RISE_SPEED.get();
        final int lifetime = ClientConfig.LIFETIME_TICKS.get();

        // Drawn back to front so a nearer number covers a further one rather than interleaving.
        final List<Popup> ordered = new ArrayList<>(POPUPS.values());
        ordered.sort((a, b) -> Double.compare(dist2(b), dist2(a)));

        for (final Popup p : ordered) {
            final float age = p.age + partial;
            final float life = age / lifetime;
            if (life >= 1.0F) {
                continue;
            }

            // Hold full opacity for the first half, then fade. Fading from the instant it appears
            // made numbers feel like they flickered.
            final float opacity = life < 0.5F ? 1.0F : 1.0F - (life - 0.5F) * 2.0F;
            final int alpha = (int) (opacity * 255.0F);
            if (alpha < 8) {
                continue;
            }

            final float worldY = (float) p.y + (age / 20.0F) * rise;
            final Vector4f clip = new Vector4f(
                    (float) (p.x - camX), worldY - (float) camY, (float) (p.z - camZ), 1.0F);
            clip.mul(VIEW_PROJ);

            // Behind the camera: w flips sign and the projection would mirror it onto the screen.
            if (clip.w <= 0.0F) {
                continue;
            }

            final float sx = (clip.x / clip.w * 0.5F + 0.5F) * screenW;
            final float sy = (1.0F - (clip.y / clip.w * 0.5F + 0.5F)) * screenH;

            final String text = format(p);
            final int argb = (alpha << 24) | p.rgb;

            pose.m_85836_();
            pose.m_85837_(sx, sy, 0.0D);
            pose.m_85841_(scale, scale, 1.0F);

            final int half = font.m_92895_(text) / 2;
            // Drop shadow on: these are read against fire, lava and Nether brick, and unshadowed
            // text disappears into all three.
            g.m_280056_(font, text, -half, -font.f_92710_ / 2, argb, true);

            pose.m_85849_();
        }
    }

    private static double dist2(final Popup p) {
        final double dx = p.x - camX;
        final double dy = p.y - camY;
        final double dz = p.z - camZ;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * {@code 150} for a single hit, {@code 150 (450) x3} once a combo is running -- this hit, the
     * combo's running total, and how many hits are in it.
     */
    private static String format(final Popup p) {
        final StringBuilder sb = new StringBuilder(BOLD).append(trim(p.lastHit));
        if (p.hits > 1) {
            sb.append(" (").append(trim(p.total)).append(") x").append(p.hits);
        }
        return sb.toString();
    }

    /**
     * Whole numbers below 1000, one decimal only when it carries information, and thousands
     * abbreviated -- a Solo Leveling Strength build hits for six figures and "127431.6" is not a
     * number anyone reads mid-swing.
     */
    private static String trim(final float value) {
        if (value >= 10000.0F) {
            return String.format("%.1fk", value / 1000.0F);
        }
        if (value >= 100.0F || value == Math.round(value)) {
            return Integer.toString(Math.round(value));
        }
        final String s = String.format("%.1f", value);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Leaving a world must not carry its popups into the next one. */
    @SubscribeEvent
    public static void onLoggedOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        POPUPS.clear();
        viewReady = false;
    }
}
