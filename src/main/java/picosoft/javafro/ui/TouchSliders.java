package picosoft.javafro.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;

final class TouchSliders {
    private TouchSliders() {}
    static void install(JSlider slider) {
        slider.setUI(new BasicSliderUI(slider) {
            @Override protected Dimension getThumbSize() { return new Dimension(44, 44); }
            @Override public void paintThumb(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(slider.isEnabled() ? new Color(0x987047) : SandTheme.BORDER);
                    g.fillOval(thumbRect.x + 6, thumbRect.y + 6, 32, 32);
                } finally { g.dispose(); }
            }
            @Override public void paintTrack(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setColor(SandTheme.BORDER);
                    g.fillRoundRect(trackRect.x, trackRect.y + trackRect.height / 2 - 3, trackRect.width, 6, 6, 6);
                } finally { g.dispose(); }
            }
        });
        slider.setPreferredSize(new Dimension(slider.getPreferredSize().width, slider.getPaintTicks() ? 58 : 48));
    }
}
