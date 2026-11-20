package picosoft.javafro.ui;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.util.Arrays;
import java.util.Map;

/** Warm Nimbus palette, applied before creating the player components. */
public final class SandTheme {
    public static final Color INK = new Color(0x443429);
    public static final Color CURRENT_LINE = new Color(0xF6EEE3);
    public static final Color SELECTION = new Color(0xE3C8AB);
    public static final Color GUTTER = new Color(0xF3EBDD);
    public static final Color BORDER = new Color(0xCBB79A);
    public static final Color MUTED = new Color(0x89745C);
    /** Quoted strings (rhythm patterns) in the YAML editor; keep in sync with web Editor.tsx. */
    public static final Color EDITOR_STRING = new Color(0x6B3A1E);
    /** Extra space between editor lines, as a fraction of the font size. */
    public static final float EDITOR_LINE_SPACING = 0.3f;
    /** Letter spacing in quoted strings, in em; keep in sync with web editor-setup.ts. */
    private static final float EDITOR_STRING_TRACKING = 0.1f;
    private static final String EDITOR_FONT_FAMILY = "Consolas";

    private SandTheme() { }

    /** Bold, letter-spaced variant of the editor font for quoted strings (rhythm patterns). */
    public static Font editorStringFont(Font editorFont) {
        return editorFont.deriveFont(Font.BOLD).deriveFont(Map.of(TextAttribute.TRACKING, EDITOR_STRING_TRACKING));
    }

    /** Consolas when installed; otherwise Java's logical monospaced font, never a proportional fallback. */
    public static Font editorFont(int size) {
        boolean installed = false;
        try {
            installed = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames()).contains(EDITOR_FONT_FAMILY);
        } catch (RuntimeException ignored) {
            // Font enumeration unavailable; use the logical font below.
        }
        return new Font(installed ? EDITOR_FONT_FAMILY : Font.MONOSPACED, Font.PLAIN, size);
    }

    public static void install() throws UnsupportedLookAndFeelException {
        color("control", 0xEADCC7);
        color("nimbusBase", 0x866343);
        color("nimbusBlueGrey", 0xCBB79A);
        color("nimbusLightBackground", 0xFFFCF7);
        color("nimbusFocus", 0xA77A4D);
        color("nimbusSelectionBackground", 0x896442);
        color("nimbusSelectedText", 0xFFFFFF);
        color("nimbusDisabledText", 0x968571);
        color("text", 0x443429);
        color("textHighlight", 0xDFC5A2);
        color("textHighlightText", 0x443429);
        UIManager.setLookAndFeel(new NimbusLookAndFeel());
    }

    private static void color(String key, int rgb) {
        UIManager.put(key, new ColorUIResource(new Color(rgb)));
    }
}
