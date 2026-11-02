package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.Test;
import picosoft.javafro.io.RhythmLoader;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class YamlFoldingTest {
    @Test public void quotedNamesCommentsAndUnicodeKeepCorrectFoldBoundaries() throws Exception {
        String source = "# 🥁\n\"Prvý: rytmus\":\n  tracks: {djembe: 'd.tt..s.'}\n"
                + "# comment\n\"Druhý rytmus\":\n  tracks: {djembe: 's.ts..s.'}";
        SwingUtilities.invokeAndWait(() -> {
            var editor = findEditor(new RhythmPlayerPanel(source));
            var manager = editor.getFoldManager();
            assertEquals(2, manager.getFoldCount());
            assertEquals(source.indexOf("\"Prvý"), manager.getFold(0).getStartOffset());
            assertEquals(source.indexOf("\"Druhý"), manager.getFold(1).getStartOffset());
            assertTrue(manager.getFold(0).getEndOffset() < manager.getFold(1).getStartOffset());
        });
    }

    @Test public void libraryOpensWithRhythmsCollapsedAndCanBeUnfoldedAndEdited() throws Exception {
        String source = Files.readString(Path.of("rhythms/basicRhythms.yaml"));
        int count = RhythmLoader.load(Path.of("rhythms/basicRhythms.yaml")).size();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(source);
            var editor = findEditor(panel);
            assertNotNull(editor);
            assertTrue(editor.isCodeFoldingEnabled());
            var manager = editor.getFoldManager();
            assertEquals(count, manager.getFoldCount());
            for (int i = 0; i < count; i++) assertTrue(manager.getFold(i).isCollapsed());
            String text = editor.getText();
            var first = manager.getFold(0);
            first.setCollapsed(false);
            for (int i = 0; i < first.getChildCount(); i++) assertFalse(first.getChild(i).isCollapsed());
            assertEquals(text, editor.getText());
            try {
                editor.insert(" # edit", editor.getLineEndOffset(first.getStartLine()) - 1);
            } catch (javax.swing.text.BadLocationException exception) {
                throw new AssertionError(exception);
            }
            manager.reparse();
            assertFalse(manager.getFold(0).isCollapsed());
            assertTrue(manager.getFold(1).isCollapsed());
        });
    }

    private RSyntaxTextArea findEditor(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof RSyntaxTextArea editor) return editor;
            if (child instanceof Container container) {
                var found = findEditor(container);
                if (found != null) return found;
            }
        }
        return null;
    }
}
