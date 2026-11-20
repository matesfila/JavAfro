package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import java.awt.Font;

/**
 * RSyntaxTextArea with a configurable line height. RSTA has no line-spacing setting, but every
 * view, the gutter and the caret read line geometry through these two getters.
 */
final class SpacedSyntaxTextArea extends RSyntaxTextArea {
    /** Line height as a multiple of the font size, like CSS line-height; 0 keeps the font's own height. */
    private double lineHeight;

    SpacedSyntaxTextArea(int rows, int columns) {
        super(rows, columns);
    }

    void setLineHeight(double lineHeight) {
        this.lineHeight = lineHeight;
        revalidate();
        repaint();
    }

    private int extraSpace() {
        Font font = getFont(); // null while the superclass constructor runs
        if (font == null) return 0;
        return Math.max(0, (int) Math.round(font.getSize2D() * lineHeight) - super.getLineHeight());
    }

    @Override public int getLineHeight() {
        return super.getLineHeight() + extraSpace();
    }

    /** Moves the baseline down by half the extra space, so the text stays centred in its line. */
    @Override public int getMaxAscent() {
        return super.getMaxAscent() + extraSpace() / 2;
    }
}
