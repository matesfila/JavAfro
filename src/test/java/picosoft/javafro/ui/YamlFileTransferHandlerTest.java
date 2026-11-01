package picosoft.javafro.ui;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class YamlFileTransferHandlerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Transferable files(File... files) {
        return new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.javaFileListFlavor}; }
            public boolean isDataFlavorSupported(DataFlavor flavor) { return flavor.equals(DataFlavor.javaFileListFlavor); }
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                return List.of(files);
            }
        };
    }

    private JTextArea editor(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTextArea text) return text;
            if (child instanceof Container nested) {
                JTextArea found = editor(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    public void emptyEditorAcceptsDroppedYamlWithSpacesAndKeepsTextPaste() throws Exception {
        var yaml = temporary.newFile("Môj rytmus.YML");
        String source = "Test:\n  name: Prvý rytmus\n  bpm: 123\n  tracks:\n    dundun: x.......\n";
        Files.writeString(yaml.toPath(), source);
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel("");
            var text = editor(panel);
            assertNotNull(text);
            assertEquals("", text.getText());
            assertTrue(panel.getTransferHandler().importData(new TransferHandler.TransferSupport(panel, files(yaml))));
            assertEquals(source, text.getText());
            text.setCaretPosition(text.getDocument().getLength());
            assertTrue(text.getTransferHandler().importData(text, new StringSelection("# pasted text\n")));
            assertEquals(source + "# pasted text\n", text.getText());
            var clipboard = new Clipboard("test");
            text.selectAll();
            text.getTransferHandler().exportToClipboard(text, clipboard, TransferHandler.COPY);
            try {
                assertEquals(text.getText(), clipboard.getData(DataFlavor.stringFlavor));
            } catch (Exception exception) { throw new AssertionError(exception); }
        });
    }

    @Test
    public void rejectsMultipleFilesDirectoriesWrongExtensionsAndBusyPlayback() throws Exception {
        var yaml = temporary.newFile("one.yaml");
        var other = temporary.newFile("two.yaml");
        var image = temporary.newFile("image.png");
        var opened = new ArrayList<java.nio.file.Path>();
        var errors = new ArrayList<Exception>();
        var handler = new YamlFileTransferHandler(null, () -> true, opened::add, errors::add);
        var target = new JPanel();
        for (Transferable value : List.of(files(yaml, other), files(temporary.getRoot()), files(image))) {
            assertFalse(handler.importData(new TransferHandler.TransferSupport(target, value)));
        }
        var busy = new YamlFileTransferHandler(null, () -> false, opened::add, errors::add);
        assertFalse(busy.canImport(new TransferHandler.TransferSupport(target, files(yaml))));
        assertFalse(busy.importData(new TransferHandler.TransferSupport(target, files(yaml))));
        assertTrue(opened.isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    public void openingFailureIsReportedAsFailedDrop() throws Exception {
        var yaml = temporary.newFile("broken.yaml");
        var errors = new ArrayList<Exception>();
        var handler = new YamlFileTransferHandler(null, () -> true,
                path -> { throw new IllegalArgumentException("Invalid YAML"); }, errors::add);
        var target = new JPanel();
        assertFalse(handler.importData(new TransferHandler.TransferSupport(target, files(yaml))));
        assertEquals(1, errors.size());
    }
}
