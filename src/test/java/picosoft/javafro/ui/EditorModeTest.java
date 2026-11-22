package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.TextMode;
import org.junit.Test;
import javax.swing.*;
import java.awt.event.ActionEvent;
import static org.junit.Assert.*;

public class EditorModeTest {
    @Test public void menuItemAndInsertKeyToggleTheSameEditorMode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel("Test:\n  tracks: {djembe: 'b.tt..s.'}");
            var editor = (RSyntaxTextArea) NotePadTest.find(panel, "yamlEditor");
            var item = (JCheckBoxMenuItem) NotePadTest.menuItem(panel, "insertModeItem");
            assertEquals(Boolean.TRUE, item.getClientProperty("CheckBoxMenuItem.doNotCloseOnMouseClick"));
            assertEquals(TextMode.INSERT, editor.getTextMode());
            editor.setCaretPosition(1);
            item.doClick(0);
            assertEquals(TextMode.OVERWRITE, editor.getTextMode());
            assertEquals("Prepisovanie", item.getText());
            assertEquals(1, editor.getCaretPosition());
            editor.replaceSelection("x");
            assertTrue(editor.getText().startsWith("Txst:"));
            var key = editor.getInputMap().get(KeyStroke.getKeyStroke("INSERT"));
            editor.getActionMap().get(key).actionPerformed(new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));
            assertEquals(TextMode.INSERT, editor.getTextMode());
            assertFalse(item.isSelected());
        });
    }
}
