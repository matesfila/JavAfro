package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.junit.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.font.TextAttribute;
import static org.junit.Assert.*;

public class EditorZoomTest {
    private static final String YAML = "Test:\n  bpm: 120\n  stepsPerBeat: 4\n  tracks: {djembe: 'b.tt..s.'}\n"
            + "# line\n".repeat(200);

    @Test public void ctrlWheelZoomsTheFontAndKeepsTheStringStyle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor(new RhythmPlayerPanel(YAML));
            var scroll = scrollPane(editor);
            assertEquals(16, editor.getFont().getSize()); // AppConfig.defaults()
            wheel(scroll, InputEvent.CTRL_DOWN_MASK, -1);
            wheel(scroll, InputEvent.CTRL_DOWN_MASK, -1);
            assertEquals(18, editor.getFont().getSize());
            assertEquals(36, editor.getLineHeight()); // default lineHeight 2
            Font string = editor.getSyntaxScheme().getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).font;
            assertEquals(18, string.getSize());
            assertTrue(string.isBold());
            assertEquals(0.1f, string.getAttributes().get(TextAttribute.TRACKING));
            wheel(scroll, InputEvent.CTRL_DOWN_MASK, 1);
            assertEquals(17, editor.getFont().getSize());
        });
    }

    @Test public void zoomStaysWithinTheConfiguredFontSizeRange() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor(new RhythmPlayerPanel(YAML));
            var scroll = scrollPane(editor);
            wheel(scroll, InputEvent.CTRL_DOWN_MASK, 100);
            assertEquals(EditorZoom.MIN_FONT_SIZE, editor.getFont().getSize());
            wheel(scroll, InputEvent.CTRL_DOWN_MASK, -100);
            assertEquals(EditorZoom.MAX_FONT_SIZE, editor.getFont().getSize());
        });
    }

    @Test public void touchpadPartialRotationsAddUpToWholeSteps() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor(new RhythmPlayerPanel(YAML));
            var scroll = scrollPane(editor);
            wheel(scroll, InputEvent.CTRL_DOWN_MASK, -0.5);
            assertEquals(16, editor.getFont().getSize());
            wheel(scroll, InputEvent.CTRL_DOWN_MASK, -0.5);
            assertEquals(17, editor.getFont().getSize());
        });
    }

    @Test public void wheelWithoutCtrlStillScrolls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor(new RhythmPlayerPanel(YAML));
            var scroll = scrollPane(editor);
            wheel(scroll, 0, 3);
            assertEquals(16, editor.getFont().getSize());
            assertTrue(scroll.getViewport().getViewPosition().y > 0);
        });
    }

    @Test public void zoomKeepsTheLineUnderTheMouseInPlace() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor(new RhythmPlayerPanel(YAML));
            var scroll = scrollPane(editor);
            JViewport viewport = scroll.getViewport();
            viewport.setViewPosition(new Point(0, 40 * editor.getLineHeight()));
            Point mouse = SwingUtilities.convertPoint(viewport, new Point(20, 100), scroll);
            int lineUnderMouse = editor.viewToModel2D(new Point(20, viewport.getViewPosition().y + 100));
            wheel(scroll, InputEvent.CTRL_DOWN_MASK, -4, mouse);
            assertEquals(20, editor.getFont().getSize());
            int after = editor.viewToModel2D(new Point(20, viewport.getViewPosition().y + 100));
            assertEquals(line(editor, lineUnderMouse), line(editor, after));
        });
    }

    private static int line(RSyntaxTextArea editor, int offset) {
        try {
            return editor.getLineOfOffset(offset);
        } catch (javax.swing.text.BadLocationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void wheel(JScrollPane scroll, int modifiers, double rotation) {
        wheel(scroll, modifiers, rotation, new Point(scroll.getWidth() / 2, scroll.getHeight() / 2));
    }

    private static void wheel(JScrollPane scroll, int modifiers, double rotation, Point at) {
        scroll.dispatchEvent(new MouseWheelEvent(scroll, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                modifiers, at.x, at.y, at.x, at.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3,
                (int) rotation, rotation));
    }

    private static RSyntaxTextArea editor(RhythmPlayerPanel panel) {
        return (RSyntaxTextArea) find(panel, "yamlEditor");
    }

    private static JScrollPane scrollPane(RSyntaxTextArea editor) {
        var scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, editor);
        scroll.setSize(500, 400);
        scroll.doLayout(); // no window: validate() would not lay out without a native peer
        scroll.getViewport().doLayout();
        return scroll;
    }

    private static Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) {
                Component found = find(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
