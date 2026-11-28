package picosoft.javafro.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.ui.FlatLineBorder;
import picosoft.javafro.config.AppConfig;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.font.TextAttribute;
import java.util.Arrays;
import java.util.Map;

/**
 * Warm paper palette of the web player (web/src/style.css) on top of FlatLaf.
 * {@link #install()} runs before the player components are created; the helpers also work without it (tests).
 */
public final class SandTheme {
    public static final Color INK = new Color(0x443429);
    public static final Color BACKGROUND = new Color(0xF5F0E8);
    public static final Color PAPER = new Color(0xFFFCF8);
    public static final Color CURRENT_LINE = new Color(0xF8F0E3);
    public static final Color SELECTION = new Color(0xE4D5C1);
    public static final Color GUTTER = new Color(0xF7F0E5);
    public static final Color BORDER = new Color(0xE1D7C9);
    public static final Color MUTED = new Color(0x867563);
    public static final Color LABEL = new Color(0x705840);
    public static final Color ACCENT = new Color(0x805638);
    public static final Color ON_ACCENT = new Color(0xFFFAF4);
    /** The soft web Live green {@code rgb(84,140,86)} and red {@code rgb(196,98,84)} at 85% over {@link #PAPER}. */
    public static final Color PLAY = new Color(0x6E9D6E);
    public static final Color STOP = new Color(0xCD796D);
    public static final Color CARET = new Color(0x745137);
    public static final Color SLIDER = new Color(0x987047);
    public static final Color TRACK = new Color(0xD3C2AC);
    public static final Color KEY = new Color(0xF2EADF);
    public static final Color MESSAGE = new Color(0x8D4E35);
    public static final Color ERROR = new Color(0xA23F2D);
    public static final Color ERROR_BACKGROUND = new Color(0xFFF3ED);

    private SandTheme() { }

    /** Editor font variant for quoted strings (rhythm patterns), as configured in {@code editor.strings}. */
    public static Font editorStringFont(Font editorFont, AppConfig.EditorStyle style) {
        Font font = editorFont.deriveFont(style.stringBold() ? Font.BOLD : Font.PLAIN);
        // Tracking is in em, like CSS letter-spacing; RSTA measures and draws it consistently.
        return style.stringLetterSpacing() == 0 ? font
                : font.deriveFont(Map.of(TextAttribute.TRACKING, (float) style.stringLetterSpacing()));
    }

    /** The configured family when installed; otherwise Java's logical monospaced font, never a proportional fallback. */
    public static Font editorFont(String family, int size) {
        boolean installed = false;
        try {
            installed = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames()).contains(family);
        } catch (RuntimeException ignored) {
            // Font enumeration unavailable; use the logical font below.
        }
        return new Font(installed ? family : Font.MONOSPACED, Font.PLAIN, size);
    }

    /** FlatLaf Light with the web palette; values use FlatLaf's properties syntax. */
    public static void install() {
        FlatLaf.setGlobalExtraDefaults(Map.ofEntries(
                Map.entry("@background", "#f5f0e8"),
                Map.entry("@foreground", "#44372e"),
                Map.entry("@componentBackground", "#fffcf8"),
                Map.entry("@menuBackground", "#fffcf8"),
                Map.entry("@accentColor", "#805638"),
                Map.entry("defaultFont", "+2"),
                Map.entry("Component.arc", "7"),
                Map.entry("Button.arc", "7"),
                Map.entry("TextComponent.arc", "7"),
                Map.entry("Component.borderColor", "#e1d7c9"),
                Map.entry("Component.disabledBorderColor", "#ece4d8"),
                Map.entry("Component.focusColor", "#b88a5b"),
                Map.entry("Component.focusedBorderColor", "#b88a5b"),
                Map.entry("Button.background", "#fffcf8"),
                Map.entry("Button.foreground", "#6f5843"),
                Map.entry("Button.borderColor", "#e1d7c9"),
                Map.entry("Button.hoverBackground", "#f1e8da"),
                Map.entry("Button.pressedBackground", "#eaddcb"),
                Map.entry("ToggleButton.background", "#f2eadf"),
                Map.entry("ToggleButton.selectedBackground", "#805638"),
                Map.entry("ToggleButton.selectedForeground", "#fffaf4"),
                Map.entry("Slider.thumbColor", "#987047"),
                Map.entry("Slider.trackColor", "#d3c2ac"),
                Map.entry("Slider.trackValueColor", "#987047"),
                Map.entry("Separator.foreground", "#e1d7c9"),
                Map.entry("PopupMenu.borderColor", "#e1d7c9"),
                Map.entry("MenuItem.selectionBackground", "#f1e8da"),
                Map.entry("MenuItem.selectionForeground", "#44372e"),
                Map.entry("ScrollBar.thumb", "#d8cbb9"),
                Map.entry("ScrollBar.hoverThumbColor", "#c6b49c"),
                Map.entry("SplitPaneDivider.gripColor", "#c6b49c"),
                Map.entry("TitlePane.background", "#f5f0e8"),
                Map.entry("TitlePane.inactiveBackground", "#f5f0e8")));
        FlatLightLaf.setup();
    }

    /** Rounded paper card with the web's 12px radius and 1px border. */
    public static <T extends JComponent> T card(T component, int padding) {
        component.setOpaque(true);
        component.setBackground(PAPER);
        component.putClientProperty(FlatClientProperties.STYLE, "arc: 12");
        component.setBorder(new FlatLineBorder(new Insets(padding, padding, padding, padding), BORDER, 1f, 12));
        return component;
    }

    /** Filled green Play button (web {@code .play}). */
    public static <T extends AbstractButton> T play(T button) {
        return filled(button, PLAY, "#6e9d6e", "#659065", "#5c845c");
    }

    /** Filled red Stop button (web {@code .stop}). */
    public static <T extends AbstractButton> T stop(T button) {
        return filled(button, STOP, "#cd796d", "#bf7065", "#b0675d");
    }

    /**
     * The global {@code Button.hoverBackground} / {@code pressedBackground} would turn a filled button beige under
     * the mouse, so hover and press use darker shades of the button's own colour instead.
     */
    private static <T extends AbstractButton> T filled(T button, Color background, String border, String hover, String pressed) {
        button.setBackground(background);
        button.setForeground(ON_ACCENT);
        button.putClientProperty(FlatClientProperties.STYLE, "borderColor: " + border + "; focusedBorderColor: #b88a5b"
                + "; hoverBackground: " + hover + "; pressedBackground: " + pressed);
        return button;
    }

    /** Beige key-like button (web mute/solo and note keys). */
    public static <T extends AbstractButton> T key(T button) {
        button.setBackground(KEY);
        button.setForeground(LABEL);
        return button;
    }

    /** Bold section heading derived from the current default font. */
    public static Font heading(Font base, float size) {
        return base.deriveFont(Font.BOLD, size);
    }
}
