package picosoft.javafro;

import picosoft.javafro.ui.RhythmPlayerPanel;
import picosoft.javafro.ui.SandTheme;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AfroJavaPlayer {

    private static java.util.List<Image> loadIcons() throws IOException {
        try (var input = AfroJavaPlayer.class.getResourceAsStream("/icons/djembe.png")) {
            if (input == null) throw new IOException("Chýba ikona aplikácie djembe.png.");
            BufferedImage source = ImageIO.read(input);
            if (source == null) throw new IOException("Neplatná ikona aplikácie djembe.png.");
            java.util.List<Image> icons = new java.util.ArrayList<>();
            for (int size : new int[]{16, 20, 24, 32, 48, 64, 128, 256}) {
                BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = icon.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(source, 0, 0, size, size, null);
                graphics.dispose();
                icons.add(icon);
            }
            return icons;
        }
    }

    static String readYaml(String[] args) throws IOException {
        if (args.length == 0) return "";
        if (args.length != 1 || args[0].isBlank()) {
            throw new IllegalArgumentException("Použitie: AfroJavaPlayer [cesta-k-rytmu.yaml]\n"
                    + "Zadaj najviac jeden YAML súbor. Cestu s medzerami uzavri do úvodzoviek.");
        }
        Path rhythmFile = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(rhythmFile)) {
            throw new IOException("Súbor rytmu neexistuje alebo nie je bežný súbor: " + rhythmFile);
        }
        return Files.readString(rhythmFile, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                SandTheme.install();
                String yaml = readYaml(args);
                RhythmPlayerPanel player = new RhythmPlayerPanel(yaml);
                JFrame frame = new JFrame("JavAfro");
                frame.setIconImages(loadIcons());
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.setContentPane(player);
                frame.setMinimumSize(new Dimension(760, 560));
                frame.pack();
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                frame.setSize(Math.min(1200, screen.width - 80), Math.min(900, screen.height - 100));
                frame.setLocationRelativeTo(null);
                frame.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent event) { player.stopPlayback(); }
                });
                frame.setVisible(true);
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(null, exception.getMessage(), "JavAfro", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

}
