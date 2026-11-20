package picosoft.javafro.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;

/** Slider with a 44px hit area and the web's thumb/track colors (style.css input[type=range]). */
final class TouchSliders {
    private TouchSliders() {}
    static void install(JSlider slider) {
        slider.setOpaque(false);
        slider.setUI(new BasicSliderUI(slider) {
            @Override protected Dimension getThumbSize() { return new Dimension(44, 44); } // touch hit area; the drawn thumb is smaller
            @Override public void paintThumb(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int size = 20;
                    int x = thumbRect.x + (thumbRect.width - size) / 2, y = thumbRect.y + (thumbRect.height - size) / 2;
                    g.setColor(slider.isEnabled() ? SandTheme.SLIDER : SandTheme.TRACK);
                    g.fillOval(x, y, size, size);
                    if (slider.hasFocus()) {
                        g.setColor(new Color(0xB88A5B));
                        g.setStroke(new BasicStroke(2f));
                        g.drawOval(x - 3, y - 3, size + 6, size + 6);
                    }
                } finally { g.dispose(); }
            }
            @Override public void paintTrack(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int y = trackRect.y + trackRect.height / 2 - 3;
                    g.setColor(SandTheme.TRACK);
                    g.fillRoundRect(trackRect.x, y, trackRect.width, 6, 6, 6);
                    // Fill from the minimum (or from zero for symmetric ranges such as dialect offsets).
                    int origin = slider.getMinimum() < 0 && slider.getMaximum() > 0 ? xPositionForValue(0) : trackRect.x;
                    int thumb = thumbRect.x + thumbRect.width / 2;
                    g.setColor(slider.isEnabled() ? SandTheme.SLIDER : SandTheme.BORDER);
                    g.fillRoundRect(Math.min(origin, thumb), y, Math.abs(thumb - origin), 6, 6, 6);
                } finally { g.dispose(); }
            }
            @Override public void paintFocus(Graphics graphics) { }
        });
        slider.setPreferredSize(new Dimension(slider.getPreferredSize().width, slider.getPaintTicks() ? 52 : 44));
    }
}
