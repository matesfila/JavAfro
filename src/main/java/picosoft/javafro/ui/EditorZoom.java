package picosoft.javafro.ui;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Rectangle2D;
import java.util.function.IntConsumer;

/** Ctrl + mouse wheel changes the editor font size; the wheel alone keeps scrolling. */
final class EditorZoom {
    /** Same range as {@code editor.fontSize} in javafro.yaml and the web pinch zoom. */
    static final int MIN_FONT_SIZE = 8;
    static final int MAX_FONT_SIZE = 72;

    private final JScrollPane scroll;
    private final JTextArea editor;
    private final IntConsumer setFontSize;
    private final MouseWheelListener[] scrolling;
    /** Partial wheel rotation from high-resolution wheels and touchpads. */
    private double pendingSteps;

    private EditorZoom(JScrollPane scroll, JTextArea editor, IntConsumer setFontSize) {
        this.scroll = scroll;
        this.editor = editor;
        this.setFontSize = setFontSize;
        this.scrolling = scroll.getMouseWheelListeners();
    }

    /**
     * Wheel events over the text and the gutter reach the scroll pane, because the editor has no
     * wheel listener. The scroll pane's own listeners are wrapped, so a zoom does not also scroll.
     */
    static void install(JScrollPane scroll, JTextArea editor, IntConsumer setFontSize) {
        EditorZoom zoom = new EditorZoom(scroll, editor, setFontSize);
        for (MouseWheelListener listener : zoom.scrolling) scroll.removeMouseWheelListener(listener);
        scroll.addMouseWheelListener(zoom::wheelMoved);
    }

    private void wheelMoved(MouseWheelEvent event) {
        if (!event.isControlDown()) {
            for (MouseWheelListener listener : scrolling) listener.mouseWheelMoved(event);
            return;
        }
        event.consume();
        pendingSteps -= event.getPreciseWheelRotation(); // wheel up (away from the user) enlarges
        int steps = (int) pendingSteps;
        if (steps == 0) return;
        pendingSteps -= steps;
        int size = editor.getFont().getSize();
        int next = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size + steps));
        if (next != size) zoomAround(SwingUtilities.convertPoint(scroll, event.getPoint(), scroll.getViewport()), size, next);
    }

    /** Changes the font size and keeps the text under {@code mouse} (viewport coordinates) in place. */
    private void zoomAround(Point mouse, int size, int next) {
        JViewport viewport = scroll.getViewport();
        Point view = viewport.getViewPosition();
        Point inEditor = new Point(view.x + mouse.x, view.y + mouse.y);
        int offset = editor.viewToModel2D(inEditor);
        double lineFraction = 0;
        try {
            Rectangle2D line = editor.modelToView2D(offset);
            if (line != null && line.getHeight() > 0) lineFraction = (inEditor.y - line.getY()) / line.getHeight();
        } catch (BadLocationException ignored) {
            // The offset comes from viewToModel2D, so it is always valid.
        }
        setFontSize.accept(next);
        // Apply the new preferred size before positioning the viewport. Direct layout, not validate(),
        // so it also works before the component is shown (validate() needs a native peer).
        scroll.doLayout();
        viewport.doLayout();
        try {
            Rectangle2D line = editor.modelToView2D(offset);
            if (line == null) return;
            int y = (int) Math.round(line.getY() + lineFraction * line.getHeight()) - mouse.y;
            int x = (int) Math.round(inEditor.x * (double) next / size) - mouse.x;
            Dimension extent = viewport.getExtentSize();
            Dimension viewSize = viewport.getViewSize();
            viewport.setViewPosition(new Point(
                    Math.max(0, Math.min(x, viewSize.width - extent.width)),
                    Math.max(0, Math.min(y, viewSize.height - extent.height))));
        } catch (BadLocationException ignored) {
            // Same offset as above; the document did not change.
        }
    }
}
