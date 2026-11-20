package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import static org.junit.Assert.*;

public class RhythmMoveTest {
    private static final String SOURCE = "# library\na:\n  tracks: {djembe: B}\n\nb:\n  tracks: {djembe: T}\n# about c\nc:\n  tracks: {djembe: S}\n";
    private static final String B_FIRST = "# library\nb:\n  tracks: {djembe: T}\n# about c\na:\n  tracks: {djembe: B}\n\nc:\n  tracks: {djembe: S}\n";

    @Test public void movesTheRhythmUnderTheCaretUpWithItsTrailingComments() {
        var move = RhythmMove.move(SOURCE, SOURCE.indexOf("T}"), -1);
        String moved = move.apply(SOURCE);
        assertEquals(B_FIRST, moved);
        assertEquals("T}", moved.substring(move.caret(), move.caret() + 2));
    }

    @Test public void movesTheRhythmUnderTheCaretDown() {
        var move = RhythmMove.move(SOURCE, SOURCE.indexOf("B}"), 1);
        String moved = move.apply(SOURCE);
        assertEquals(B_FIRST, moved);
        assertEquals("B}", moved.substring(move.caret(), move.caret() + 2));
    }

    @Test public void keepsAMissingFinalLineBreakAndCrlf() {
        String text = "a:\n  bpm: 90\nb:\n  bpm: 100";
        assertEquals("b:\n  bpm: 100\na:\n  bpm: 90", RhythmMove.move(text, 0, 1).apply(text));
        String crlf = "a:\r\n  bpm: 90\r\nb:\r\n  bpm: 100";
        assertEquals("b:\r\n  bpm: 100\r\na:\r\n  bpm: 90", RhythmMove.move(crlf, crlf.indexOf("b:"), -1).apply(crlf));
    }

    @Test public void doesNothingAtTheEdgesOrInInvalidYaml() {
        assertNull(RhythmMove.move(SOURCE, SOURCE.indexOf("B}"), -1));
        assertNull(RhythmMove.move(SOURCE, SOURCE.indexOf("S}"), 1));
        assertNull(RhythmMove.move("a: [", 0, 1));
        assertNull(RhythmMove.move("a:\n  bpm: 90\n", 0, 1));
    }

    @Test public void toolbarArrowsMoveTheRhythmAsOneUndoableEdit() throws Exception {
        String yaml = "First:\n  tracks: {djembe: 'b.tt..s.'}\nSecond:\n  tracks: {djembe: 'b.tt..s.'}\n";
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(yaml);
            var editor = (RSyntaxTextArea) find(panel, "yamlEditor");
            editor.setCaretPosition(yaml.indexOf("Second") + 2);
            ((JButton) find(panel, "moveRhythmUpButton")).doClick();
            assertTrue(editor.getText().startsWith("Second:"));
            assertEquals(2, editor.getCaretPosition());
            ((JButton) find(panel, "moveRhythmDownButton")).doClick();
            assertEquals(yaml, editor.getText());
            editor.undoLastAction();
            assertTrue(editor.getText().startsWith("Second:"));
        });
    }

    @Test public void movingKeepsEveryCollapsedRhythmCollapsed() throws Exception {
        String rhythm = ":\n  bpm: 90\n  tracks: {djembe: 'b.tt..s.'}\n";
        String yaml = "A" + rhythm + "B" + rhythm + "C" + rhythm + "D" + rhythm;
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(yaml);
            var editor = (RSyntaxTextArea) find(panel, "yamlEditor");
            var folds = editor.getFoldManager();
            assertEquals(4, folds.getFoldCount());
            for (String direction : new String[]{"Up", "Down"}) {
                editor.setCaretPosition(editor.getText().indexOf("B:"));
                ((JButton) find(panel, "moveRhythm" + direction + "Button")).doClick();
                assertEquals(4, folds.getFoldCount());
                for (int i = 0; i < folds.getFoldCount(); i++) assertTrue(direction + " fold " + i, folds.getFold(i).isCollapsed());
            }
        });
    }

    private static Component find(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) {
                Component found = find(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
