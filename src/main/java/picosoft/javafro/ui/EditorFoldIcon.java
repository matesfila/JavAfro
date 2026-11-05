package picosoft.javafro.ui;

import org.fife.ui.rtextarea.FoldIndicatorIcon;
import java.awt.*;
import java.awt.geom.Path2D;

/** Larger folding chevrons, sized to fit inside one editor line. */
final class EditorFoldIcon extends FoldIndicatorIcon {
    private final int size;

    EditorFoldIcon(boolean collapsed, int size) {
        super(collapsed);
        this.size = size;
    }

    public int getIconWidth() { return size + 8; }
    public int getIconHeight() { return size; }

    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(x + 4, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SandTheme.INK);
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D path = new Path2D.Double();
            if (isCollapsed()) {
                path.moveTo(size * .35, size * .2);
                path.lineTo(size * .65, size * .5);
                path.lineTo(size * .35, size * .8);
            } else {
                path.moveTo(size * .2, size * .35);
                path.lineTo(size * .5, size * .65);
                path.lineTo(size * .8, size * .35);
            }
            g.draw(path);
        } finally { g.dispose(); }
    }
}
