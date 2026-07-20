package dev.alshabab.shababparty.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alshabab.shababparty.ShababParty;
import dev.alshabab.shababparty.network.DamageNumberPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The client's popup list, and the code that draws it.
 *
 * <p>A popup holds a world position captured at spawn rather than a live entity reference: the
 * number should stay where the hit landed even if the mob walks away or dies on the same tick, and
 * holding an Entity would keep dead entities reachable for the popup's lifetime.
 */
@Mod.EventBusSubscriber(modid = ShababParty.MOD_ID, value = Dist.CLIENT)
public final class ClientDamageNumbers {

    /** Full-bright. Damage numbers should not dim in a cave. */
    private static final int FULL_BRIGHT = 0x00F000F0;

    /** Base world-units-per-pixel for in-world text -- the vanilla nameplate constant. */
    private static final float BASE_SCALE = 0.025F;

    private static final List<Popup> POPUPS = new ArrayList<>();

    private ClientDamageNumbers() {
    }

    private static final class Popup {
        private final double x;
        private final double y;
        private final double z;
        private final String text;
        private final int rgb;
        private final int lifetime;
        private int age;

        private Popup(final double x, final double y, final double z,
                      final String text, final int rgb, final int lifetime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.text = text;
            this.rgb = rgb;
            this.lifetime = lifetime;
        }
    }

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

        final String text = format(p);
        if (text.isEmpty()) {
            return;
        }

        final Vec3 at = victim.m_146892_();

        // Same-tick hits would otherwise render exactly on top of each other. This is not polish:
        // Cataclysm's Meat Shredder ignores invincibility frames and Epic Fight combos land 3-5
        // hits per swing, so overlapping numbers are the normal case for the weapons most worth
        // measuring. Derived from list size so it stays put frame to frame.
        final double jitter = ((POPUPS.size() % 5) - 2) * 0.18D;

        POPUPS.add(new Popup(at.f_82479_ + jitter, at.f_82480_, at.f_82481_,
                text, ClientConfig.colorOf(p.bucket), ClientConfig.LIFETIME_TICKS.get()));

        // Without a cap, a Meat Shredder held down in a crowd is an unbounded allocation.
        while (POPUPS.size() > ClientConfig.MAX_POPUPS.get()) {
            POPUPS.remove(0);
        }
    }

    private static String format(final DamageNumberPacket p) {
        final boolean raw = ClientConfig.SHOW_RAW.get();
        final boolean fin = ClientConfig.SHOW_FINAL.get();
        if (raw && fin) {
            return trim(p.raw) + " (" + trim(p.finalAmount) + ")";
        }
        if (raw) {
            return trim(p.raw);
        }
        if (fin) {
            return trim(p.finalAmount);
        }
        return "";
    }

    /** One decimal place, with a trailing ".0" dropped -- "9" reads better than "9.0" mid-fight. */
    private static String trim(final float value) {
        final String s = String.format("%.1f", value);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || POPUPS.isEmpty()) {
            return;
        }
        POPUPS.removeIf(p -> ++p.age >= p.lifetime);
    }

    @SubscribeEvent
    public static void onRenderLevel(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (POPUPS.isEmpty() || !ClientConfig.ENABLED.get()) {
            return;
        }

        final Minecraft mc = Minecraft.m_91087_();
        final Font font = mc.f_91062_;
        final Camera camera = event.getCamera();
        final Vec3 camPos = camera.m_90583_();
        final PoseStack pose = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = mc.m_91269_().m_110104_();

        final float partial = event.getPartialTick();
        final float scale = (float) (double) ClientConfig.SCALE.get();
        final float rise = (float) (double) ClientConfig.RISE_SPEED.get();

        for (final Popup p : POPUPS) {
            final float age = p.age + partial;
            final float life = age / p.lifetime;
            if (life >= 1.0F) {
                continue;
            }

            final int alpha = (int) ((1.0F - life) * 255.0F);
            if (alpha < 8) {
                continue;
            }
            final int argb = (alpha << 24) | p.rgb;

            pose.m_85836_();
            pose.m_85837_(
                    p.x - camPos.f_82479_,
                    p.y - camPos.f_82480_ + (age / 20.0F) * rise,
                    p.z - camPos.f_82481_);
            // Billboard: adopting the camera's rotation makes the quad face the viewer. The
            // negative x and y scale then flips it, because in-world text is otherwise mirrored
            // and upside down.
            pose.m_252781_(camera.m_253121_());
            pose.m_85841_(-BASE_SCALE * scale, -BASE_SCALE * scale, BASE_SCALE * scale);

            final Matrix4f matrix = pose.m_85850_().m_252922_();
            final float half = font.m_92895_(p.text) / 2.0F;

            font.m_271703_(p.text, -half, 0.0F, argb, false, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);

            pose.m_85849_();
        }

        buffers.m_109911_();
    }

    /** Leaving a world must not carry its popups into the next one. */
    @SubscribeEvent
    public static void onLoggedOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        POPUPS.clear();
    }
}
