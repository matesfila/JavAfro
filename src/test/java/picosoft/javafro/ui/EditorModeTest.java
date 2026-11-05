package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.TextMode;
import org.junit.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import static org.junit.Assert.*;

public class EditorModeTest {
    @Test public void buttonAndInsertKeyToggleTheSameEditorMode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel("Test:\n  tracks: {djembe: 'b.tt..s.'}");
            var editor = (RSyntaxTextArea) find(panel, "yamlEditor");
            var button = (JToggleButton) find(panel, "insertModeButton");
            assertEquals(TextMode.INSERT, editor.getTextMode());
            editor.setCaretPosition(1);
            button.doClick();
            assertEquals(TextMode.OVERWRITE, editor.getTextMode());
            assertEquals("Overwrite mode", button.getText());
            assertEquals(1, editor.getCaretPosition());
            editor.replaceSelection("x");
            assertTrue(editor.getText().startsWith("Txst:"));
            var key = editor.getInputMap().get(KeyStroke.getKeyStroke("INSERT"));
            editor.getActionMap().get(key).actionPerformed(new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));
            assertEquals(TextMode.INSERT, editor.getTextMode());
            assertFalse(button.isSelected());
            assertEquals("Insert mode", button.getText());
        });
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
