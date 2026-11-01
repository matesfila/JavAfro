package picosoft.javafro.ui;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.Color;

/** Warm Nimbus palette, applied before creating the player components. */
public final class SandTheme {
    public static final Color INK = new Color(0x443429);
    public static final Color CURRENT_LINE = new Color(0xFAF5ED);
    public static final Color SELECTION = new Color(0xDFC5A2);
    public static final Color GUTTER = new Color(0xF3EBDD);
    public static final Color BORDER = new Color(0xCBB79A);
    public static final Color MUTED = new Color(0x89745C);

    private SandTheme() { }

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
