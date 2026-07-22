package dev.alshabab.shababparty.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Settings for the floating damage numbers.
 *
 * <p>Hand-rolled rather than Cloth Config. Cloth is in the pack and would be less code, but adding
 * it to build.sh turns a dependency-free build into one that breaks when a pack update moves Cloth's
 * version, and makes shababparty hard-require a mod it otherwise does not need.
 *
 * <p>Each bucket row is a checkbox, five preset swatches, a hex field and a live preview. The
 * swatches are drawn and hit-tested by hand because a five-item palette does not justify a widget
 * class.
 */
public final class DamageNumbersScreen extends Screen {

    private static final int ROW_HEIGHT = 28;
    private static final int SWATCH = 12;
    private static final int SWATCH_GAP = 3;

    private final Screen parent;

    private Checkbox master;
    private Checkbox showRaw;

    private final Row[] rows = {
            new Row("shababparty.damagenumbers.outgoing",
                    ClientConfig.OUTGOING_ENABLED, ClientConfig.OUTGOING_COLOR, 0xFFFF55),
            new Row("shababparty.damagenumbers.mob_to_you",
                    ClientConfig.MOB_TO_YOU_ENABLED, ClientConfig.MOB_TO_YOU_COLOR, 0xFF5555),
            new Row("shababparty.damagenumbers.player_to_you",
                    ClientConfig.PLAYER_TO_YOU_ENABLED, ClientConfig.PLAYER_TO_YOU_COLOR, 0xFF55FF),
            new Row("shababparty.damagenumbers.ally_to_mob",
                    ClientConfig.ALLY_TO_MOB_ENABLED, ClientConfig.ALLY_TO_MOB_COLOR, 0x55FFFF),
    };

    private static final class Row {
        private final String labelKey;
        private final ForgeConfigSpec.BooleanValue enabled;
        private final ForgeConfigSpec.ConfigValue<String> color;
        private final int fallback;

        private Checkbox checkbox;
        private EditBox hex;
        private int swatchX;
        private int swatchY;

        private Row(final String labelKey,
                    final ForgeConfigSpec.BooleanValue enabled,
                    final ForgeConfigSpec.ConfigValue<String> color,
                    final int fallback) {
            this.labelKey = labelKey;
            this.enabled = enabled;
            this.color = color;
            this.fallback = fallback;
        }

        private int rgb() {
            return ClientConfig.parseColor(hex == null ? color.get() : hex.m_94155_(), fallback);
        }
    }

    public DamageNumbersScreen(final Screen parent) {
        super(Component.m_237115_("shababparty.damagenumbers.title"));
        this.parent = parent;
    }

    @Override
    protected void m_7856_() {
        final int left = this.f_96543_ / 2 - 150;
        int y = 40;

        this.master = new Checkbox(left, y, 200, 20,
                Component.m_237115_("shababparty.damagenumbers.master"), ClientConfig.ENABLED.get());
        this.m_142416_(this.master);
        y += ROW_HEIGHT;

        for (final Row row : this.rows) {
            row.checkbox = new Checkbox(left, y, 150, 20,
                    Component.m_237115_(row.labelKey), row.enabled.get());
            this.m_142416_(row.checkbox);

            row.swatchX = left + 150;
            row.swatchY = y + 4;

            row.hex = new EditBox(this.f_96547_,
                    left + 150 + (SWATCH + SWATCH_GAP) * ClientConfig.PRESETS.length + 6, y,
                    56, 20, Component.m_237113_("hex"));
            row.hex.m_94199_(7);
            row.hex.m_94144_(row.color.get());
            this.m_142416_(row.hex);

            y += ROW_HEIGHT;
        }

        y += 4;
        this.showRaw = new Checkbox(left, y, 300, 20,
                Component.m_237115_("shababparty.damagenumbers.show_raw"), ClientConfig.SHOW_RAW.get());
        this.m_142416_(this.showRaw);
        y += ROW_HEIGHT + 6;

        this.m_142416_(Button.m_253074_(
                        Component.m_237115_("shababparty.damagenumbers.keybinds"),
                        b -> this.f_96541_.m_91152_(new KeyBindsScreen(this, this.f_96541_.f_91066_)))
                .m_252794_(left, y).m_253046_(145, 20).m_253136_());

        this.m_142416_(Button.m_253074_(
                        Component.m_237115_("shababparty.damagenumbers.done"), b -> this.m_7379_())
                .m_252794_(left + 155, y).m_253046_(145, 20).m_253136_());
    }

    @Override
    public boolean m_6375_(final double mouseX, final double mouseY, final int button) {
        for (final Row row : this.rows) {
            for (int i = 0; i < ClientConfig.PRESETS.length; i++) {
                final int x = row.swatchX + i * (SWATCH + SWATCH_GAP);
                if (mouseX >= x && mouseX < x + SWATCH
                        && mouseY >= row.swatchY && mouseY < row.swatchY + SWATCH) {
                    row.hex.m_94144_(ClientConfig.PRESETS[i]);
                    return true;
                }
            }
        }
        return super.m_6375_(mouseX, mouseY, button);
    }

    @Override
    public void m_88315_(final GuiGraphics g, final int mouseX, final int mouseY, final float partial) {
        this.m_280273_(g);
        super.m_88315_(g, mouseX, mouseY, partial);

        final String title = this.f_96539_.getString();
        g.m_280488_(this.f_96547_, title,
                this.f_96543_ / 2 - this.f_96547_.m_92895_(title) / 2, 18, 0xFFFFFF);

        for (final Row row : this.rows) {
            final int active = row.rgb();

            for (int i = 0; i < ClientConfig.PRESETS.length; i++) {
                final int x = row.swatchX + i * (SWATCH + SWATCH_GAP);
                final int rgb = ClientConfig.parseColor(ClientConfig.PRESETS[i], row.fallback);
                // The outlined swatch is the one currently in effect, so the bucket-to-colour
                // mapping is readable without parsing hex.
                if (rgb == active) {
                    g.m_280509_(x - 1, row.swatchY - 1,
                            x + SWATCH + 1, row.swatchY + SWATCH + 1, 0xFFFFFFFF);
                }
                g.m_280509_(x, row.swatchY, x + SWATCH, row.swatchY + SWATCH, 0xFF000000 | rgb);
            }

            // Live preview of whatever is in the hex field right now.
            final int previewX = row.hex.m_252754_() + row.hex.m_5711_() + 6;
            g.m_280509_(previewX, row.swatchY,
                    previewX + SWATCH, row.swatchY + SWATCH, 0xFF000000 | active);
        }
    }

    /**
     * Everything is written on close rather than on every keystroke: a half-typed hex string is not
     * a colour, and writing it would churn the config file and log a warning per character.
     */
    @Override
    public void m_7379_() {
        ClientConfig.ENABLED.set(this.master.m_93840_());
        ClientConfig.SHOW_RAW.set(this.showRaw.m_93840_());

        for (final Row row : this.rows) {
            row.enabled.set(row.checkbox.m_93840_());

            final String typed = row.hex.m_94155_();
            final String clean = typed.startsWith("#") ? typed.substring(1) : typed;
            // Reject junk by keeping the previous value rather than storing something the renderer
            // would have to warn about on every frame.
            if (clean.length() == 6 && clean.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
                row.color.set(clean.toUpperCase());
            }
        }

        ClientConfig.SPEC.save();
        this.f_96541_.m_91152_(this.parent);
    }
}
