import picosoft.javafro.ui.RhythmPlayerPanel;
import picosoft.javafro.ui.SandTheme;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Renders isolated Swing components without opening windows or reading the user's drafts. */
public class DesktopUiPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("target/desktop-preview"); Files.createDirectories(output);
        String yaml = Files.readString(Path.of("rhythms/basicRhythms.yaml"));
        try { SwingUtilities.invokeAndWait(() -> {
            try {
                SandTheme.install();
                // Nimbus consults the desktop pointer while constructing its default
                // slider UI. All player sliders replace that UI immediately anyway.
                UIManager.put("SliderUI", "javax.swing.plaf.basic.BasicSliderUI");
                var panel = new RhythmPlayerPanel(yaml);
                render(panel, 1200, 820, output.resolve("desktop.png"));
                render(panel, 760, 560, output.resolve("desktop-small.png"));
            } catch (Exception exception) { throw new RuntimeException(exception); }
        }); } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        System.out.println("Swing layout previews: " + output.toAbsolutePath());
        System.exit(0);
    }
    private static void render(JComponent panel, int width, int height, Path path) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            // RSyntaxTextArea normally initializes these metrics from its first
            // on-screen Graphics. Supply the image Graphics for this isolated render.
            var editor = find(panel, "yamlEditor");
            var refresh = editor.getClass().getDeclaredMethod("refreshFontMetrics", Graphics2D.class);
            refresh.setAccessible(true); refresh.invoke(editor, graphics);
            var uninitialized = editor.getClass().getDeclaredField("metricsNeverRefreshed");
            uninitialized.setAccessible(true); uninitialized.setBoolean(editor, false);
            panel.setSize(width, height); layout(panel); panel.printAll(graphics);
        } finally { graphics.dispose(); }
        ImageIO.write(image, "png", path.toFile());
    }
    private static void layout(Container parent) { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container nested) layout(nested); }
    private static Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) { Component found = find(nested, name); if (found != null) return found; }
        }
        return null;
    }
}
