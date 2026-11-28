package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.TextMode;
import org.junit.Test;
import javax.swing.*;
import java.awt.*;
import static org.junit.Assert.*;

public class NotePadTest {
    private static final String YAML = "Test:\n  tracks:\n    djembe: \"b.tt\"\n    balafon: \"c1 .\"\n";

    @Test public void notesModeShowsTheKeyboardForTheTrackUnderTheCaret() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML);
            panel.setAllFolds(false); // the incomplete rhythm is folded on load like any other
            var editor = (RSyntaxTextArea) find(panel, "yamlEditor");
            var pad = (NotePad) find(panel, "notePad");
            assertFalse(pad.isVisible());
            menuItem(panel, "notesModeItem").doClick(0);
            assertTrue(pad.isVisible());
            editor.setCaretPosition(YAML.indexOf("b.tt") + 1);
            assertEquals("djembe", pad.instrument());
            assertNotNull(find(pad, "note-s"));
            assertNull(find(pad, "note-c1"));
            editor.setCaretPosition(YAML.indexOf("c1 ."));
            assertEquals("balafon", pad.instrument());
            assertNotNull(find(pad, "note-c1"));
            editor.setCaretPosition(2);
            assertNull(pad.instrument());
            assertEquals("Vyber riadok nástroja", ((JLabel) find(pad, "noteInstrument")).getText());
        });
    }

    @Test public void insertsNotesAsSeparateUndoStepsAndHonorsOverwrite() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = new RSyntaxTextArea("djembe: \"b.tt\"");
            var pad = new NotePad(editor);
            pad.setInstrument("djembe");
            editor.setCaretPosition(9);
            click(pad, "note-s"); click(pad, "noteRest");
            assertEquals("djembe: \"s.b.tt\"", editor.getText());
            assertEquals(11, editor.getCaretPosition());
            editor.undoLastAction();
            assertEquals("djembe: \"sb.tt\"", editor.getText());
            editor.setTextMode(TextMode.OVERWRITE);
            editor.setCaretPosition(10);
            click(pad, "note-T");
            assertEquals("djembe: \"sT.tt\"", editor.getText());
            click(pad, "noteBackspace");
            assertEquals("djembe: \"s.tt\"", editor.getText());
            editor.setCaretPosition(editor.getText().length());
            click(pad, "noteRest"); // overwrite never replaces past the line end
            assertEquals("djembe: \"s.tt\".", editor.getText());
            click(pad, "noteEnter");
            assertEquals(2, editor.getLineCount());
        });
    }

    @Test public void balafonNotesAreSpaceSeparatedTokens() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = new RSyntaxTextArea("balafon: \"c1\"");
            var pad = new NotePad(editor);
            pad.setInstrument("balafon");
            editor.setCaretPosition(12);
            click(pad, "note-d1");
            assertEquals("balafon: \"c1 d1 \"", editor.getText());
            click(pad, "noteRest");
            assertEquals("balafon: \"c1 d1 . \"", editor.getText());
            editor.setTextMode(TextMode.OVERWRITE);
            editor.setCaretPosition(13); // inside "d1": the whole token and its space are replaced
            click(pad, "note-e1");
            assertEquals("balafon: \"c1 e1 . \"", editor.getText());
        });
    }

    private static void click(Container root, String name) { ((AbstractButton) find(root, name)).doClick(0); }

    /** An item of the Editor popup, which is not part of the panel's component tree. */
    static JMenuItem menuItem(RhythmPlayerPanel panel, String name) { return (JMenuItem) find(panel.editMenu(), name); }

    static Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) { Component found = find(nested, name); if (found != null) return found; }
        }
        return null;
    }
}
