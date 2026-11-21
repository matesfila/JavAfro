package picosoft.javafro.ui;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Small vector icons drawn like the inline SVGs of the web player (web/src/App.tsx, 20×20 view box).
 * They paint in the component's foreground, so they follow enabled, hover and accent colors.
 */
final class UiIcons {
    enum Shape { PLAY, STOP, FOLDER, DOWNLOAD, NEW, RESTORE, DOCUMENT, INFO, ARROW_UP, ARROW_DOWN, COLLAPSE, EXPAND }

    private UiIcons() { }

    static Icon icon(Shape shape, int size) { return new Vector(shape, size); }

    /** The djembe logo scaled to {@code height}; an empty icon if the resource is missing. */
    static Icon logo(int height) {
        try (InputStream input = UiIcons.class.getResourceAsStream("/icons/djembe.png")) {
            BufferedImage source = input == null ? null : ImageIO.read(input);
            if (source == null) return new ImageIcon(new BufferedImage(1, height, BufferedImage.TYPE_INT_ARGB));
            int width = Math.max(1, Math.round(height * (float) source.getWidth() / source.getHeight()));
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(source, 0, 0, width, height, null);
            } finally { g.dispose(); }
            return new ImageIcon(scaled);
        } catch (IOException exception) {
            return new ImageIcon(new BufferedImage(1, height, BufferedImage.TYPE_INT_ARGB));
        }
    }

    private record Vector(Shape shape, int size) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, y);
                g.scale(size / 20.0, size / 20.0);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.setColor(color(component));
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (shape) {
                    case PLAY -> g.fill(path(6, 3.5, 6, 16.5, 17, 10));
                    case STOP -> g.fillRoundRect(5, 5, 10, 10, 2, 2);
                    case FOLDER -> {
                        g.draw(path(2, 6, 2, 4, 8, 4, 10, 6, 18, 6, 18, 16, 2, 16));
                        g.drawLine(2, 8, 18, 8);
                    }
                    case DOWNLOAD -> {
                        g.drawLine(10, 2, 10, 12);
                        g.draw(open(6, 8, 10, 12, 14, 8));
                        g.draw(open(3, 13, 3, 17, 17, 17, 17, 13));
                    }
                    case NEW -> { g.drawLine(10, 3, 10, 17); g.drawLine(3, 10, 17, 10); }
                    case ARROW_UP -> g.draw(open(5, 12, 10, 7, 15, 12));
                    case ARROW_DOWN -> g.draw(open(5, 8, 10, 13, 15, 8));
                    case RESTORE -> {
                        // Counter-clockwise arc from the upper left round to the left side, with an arrowhead at its start.
                        g.draw(new java.awt.geom.Arc2D.Double(3.5, 3.5, 13, 13, 153, -321, java.awt.geom.Arc2D.OPEN));
                        g.draw(open(4, 3.5, 4, 7.5, 8, 7.5));
                    }
                    case COLLAPSE -> { g.draw(open(6, 3, 10, 7, 14, 3)); g.draw(open(6, 17, 10, 13, 14, 17)); }
                    case EXPAND -> { g.draw(open(6, 7, 10, 3, 14, 7)); g.draw(open(6, 13, 10, 17, 14, 13)); }
                    case DOCUMENT -> {
                        g.draw(path(4, 2, 12, 2, 16, 6, 16, 18, 4, 18));
                        g.drawLine(7, 10, 13, 10); g.drawLine(7, 13, 13, 13);
                    }
                    case INFO -> {
                        g.drawOval(2, 2, 16, 16);
                        g.drawLine(10, 9, 10, 14);
                        g.fillOval(9, 5, 2, 2);
                    }
                }
            } finally { g.dispose(); }
        }

        private static Color color(Component component) {
            if (component == null) return SandTheme.INK;
            if (component.isEnabled()) return component.getForeground();
            Color disabled = javax.swing.UIManager.getColor("Button.disabledText");
            return disabled == null ? SandTheme.MUTED : disabled;
        }

        private static Path2D path(double... points) {
            Path2D path = open(points);
            path.closePath();
            return path;
        }

        private static Path2D open(double... points) {
            Path2D path = new Path2D.Double();
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
            return path;
        }
    }
}
