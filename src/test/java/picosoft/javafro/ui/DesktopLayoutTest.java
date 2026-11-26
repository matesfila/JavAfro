package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Web-like two-column layout: file name marker, rhythm heading, inline mixer and help documents. */
public class DesktopLayoutTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String YAML = "First:\n  bpm: 120\n  tracks: {djembe: 'b.tt..s.'}\nSecond:\n  bpm: 90\n  tracks: {dundun: 'D.......', sangban: 'S.......'}\n";

    @Test public void headingFollowsTheRhythmUnderTheCaretWithoutMarkingTheFileModified() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML);
            var heading = (JLabel) NotePadTest.find(panel, "rhythmName");
            var fileName = (JLabel) NotePadTest.find(panel, "fileName");
            assertEquals("First", heading.getText());
            ((RSyntaxTextArea) NotePadTest.find(panel, "yamlEditor")).setCaretPosition(YAML.indexOf("Second") + 2);
            assertEquals("Second", heading.getText());
            assertEquals("rytmus.yaml", fileName.getText());
        });
    }

    @Test public void fileNameShowsUnsavedTextAndTempoChangesUntilSaved() throws Exception {
        Path target = temporary.getRoot().toPath().resolve("moj.yaml");
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML);
            var fileName = (JLabel) NotePadTest.find(panel, "fileName");
            var editor = (RSyntaxTextArea) NotePadTest.find(panel, "yamlEditor");
            editor.insert("# note\n", 0);
            assertEquals("*rytmus.yaml", fileName.getText());
            try { panel.saveTo(target); } catch (Exception exception) { throw new AssertionError(exception); }
            assertEquals("moj.yaml", fileName.getText());
            ((JSlider) NotePadTest.find(panel, "tempoSlider")).setValue(133);
            assertEquals("*moj.yaml", fileName.getText());
            try { panel.saveTo(target); } catch (Exception exception) { throw new AssertionError(exception); }
            assertEquals("moj.yaml", fileName.getText());
        });
        assertTrue(Files.readString(target).contains("bpm: 133"));
    }

    @Test public void mixerAndLiveControlsAreInlineAndInvalidYamlIsReportedInTheHeader() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML);
            var mixer = NotePadTest.find(panel, "masterVolume");
            assertNotNull(mixer);
            assertTrue(SwingUtilities.isDescendingFrom(mixer, panel));
            assertNull(SwingUtilities.getAncestorOfClass(JDialog.class, mixer));
            assertNotNull(NotePadTest.find(panel, "trackSolo-0"));
            var status = (HeaderStatus) NotePadTest.find(panel, "headerStatus");
            assertSame(NotePadTest.find(panel, "header"), status.getParent());
            assertNull(NotePadTest.find(panel, "editorStatus"));
            assertNull(NotePadTest.find(panel, "draftStatus"));
            assertEquals("", status.text());
            var editor = (RSyntaxTextArea) NotePadTest.find(panel, "yamlEditor");
            editor.insert("Broken: [\n", 0);
            editor.setCaretPosition(3);
            assertTrue(status.message(HeaderStatus.Slot.EDITOR).startsWith("Neplatný YAML"));
            assertTrue(status.isInvalid());
            assertEquals("", ((JLabel) NotePadTest.find(panel, "playbackStatus")).getText());
        });
    }

    @Test public void headerMessagesNeverMoveOrResizeAnything() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML);
            var status = panel.headerStatus();
            var header = NotePadTest.find(panel, "header");
            var brand = NotePadTest.find(panel, "brand");
            var card = NotePadTest.find(panel, "editorCard");
            var editor = (RSyntaxTextArea) NotePadTest.find(panel, "yamlEditor");
            panel.setSize(900, 600);
            layoutTree(panel);
            var before = java.util.List.of(header.getBounds(), brand.getBounds(), status.getBounds(), card.getBounds(),
                    NotePadTest.find(panel, "fileMenuButton").getBounds());
            var preferred = java.util.List.of(panel.getPreferredSize(), header.getPreferredSize(), status.getPreferredSize());
            assertEquals(0, status.getPreferredSize().width);

            editor.insert("Broken: [\n", 0);
            editor.setCaretPosition(3);
            status.setMessage(HeaderStatus.Slot.DRAFT, "rytmus.yaml · " + "veľmi dlhá správa ".repeat(40), true);
            assertTrue(status.isInvalid());
            assertTrue(status.text().contains("\n"));
            var lines = status.visibleLines();
            assertEquals(HeaderStatus.MAX_LINES, lines.size());
            assertTrue(lines.getFirst().text().startsWith("Neplatný YAML"));
            assertTrue(lines.getLast().text().endsWith("…"));
            assertEquals(preferred, java.util.List.of(panel.getPreferredSize(), header.getPreferredSize(), status.getPreferredSize()));
            layoutTree(panel);
            assertEquals(before, java.util.List.of(header.getBounds(), brand.getBounds(), status.getBounds(), card.getBounds(),
                    NotePadTest.find(panel, "fileMenuButton").getBounds()));

            status.setMessage(HeaderStatus.Slot.EDITOR, "", false);
            status.setMessage(HeaderStatus.Slot.DRAFT, "", false);
            assertEquals("", status.text());
            assertTrue(status.visibleLines().isEmpty());
            layoutTree(panel);
            assertEquals(before.get(2), status.getBounds());
            assertEquals(before.get(3), card.getBounds());
            assertFalse(status.isFullMessageShown()); // headless: never shown, never throws
            status.showFullMessage();
            assertFalse(status.isFullMessageShown());

            var image = new java.awt.image.BufferedImage(header.getWidth(), header.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            header.paint(graphics); // painting an empty and a filled slot must not fail
            status.setMessage(HeaderStatus.Slot.EDITOR, "Neplatný YAML · chyba", true);
            header.paint(graphics);
            graphics.dispose();
        });
    }

    @Test public void longWordsWrapAnywhere() {
        var metrics = new JLabel().getFontMetrics(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        int width = metrics.stringWidth("0123456789");
        assertEquals(java.util.List.of("0123456789", "0123456789", "ab cd"), HeaderStatus.wrap("01234567890123456789 ab cd", metrics, width));
        assertEquals(java.util.List.of("ab cd", "efgh"), HeaderStatus.wrap("ab cd efgh", metrics, width - 1));
        assertEquals(java.util.List.of("a", "b"), HeaderStatus.wrap("a\n\nb", metrics, width));
    }

    private static void layoutTree(java.awt.Container container) {
        container.doLayout();
        for (var child : container.getComponents()) if (child instanceof java.awt.Container nested) layoutTree(nested);
    }

    @Test public void dialectResetZeroesEveryPulse() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var changes = new java.util.ArrayList<picosoft.javafro.base.Dialect>();
            var controls = new DialectControls(changes::add, false);
            controls.setDialect(picosoft.javafro.base.Dialect.of(4, java.util.List.of(0, .3, 0, -.5)));
            ((JButton) NotePadTest.find(controls, "dialectReset")).doClick(0);
            assertEquals(picosoft.javafro.base.Dialect.straight(4), controls.value());
            assertEquals(controls.value(), changes.getLast());
            assertEquals(0, ((JSlider) NotePadTest.find(controls, "dialectPulse2")).getValue());
            ((JButton) NotePadTest.find(controls, "dialectReset")).doClick(0);
            assertEquals(1, changes.size()); // already straight: no extra change
        });
    }

    @Test public void helpDocumentsAreBundledAndHandedToTheSystemViewer() throws Exception {
        var opened = new AtomicReference<Path>();
        var documents = new HelpDocuments(opened::set);
        for (var document : HelpDocuments.Document.values()) {
            Path file = documents.open(document);
            assertEquals(file, opened.get());
            assertTrue(file.getFileName().toString().endsWith(".pdf"));
            byte[] header = new byte[5];
            try (var input = Files.newInputStream(file)) { assertEquals(5, input.read(header)); }
            assertEquals("%PDF-", new String(header, java.nio.charset.StandardCharsets.US_ASCII));
            Files.deleteIfExists(file);
        }
    }

    @Test public void aboutDialogShowsVersionAndClosesItself() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var closed = new boolean[1];
            JPanel content = AboutDialog.content(() -> closed[0] = true);
            ((JButton) NotePadTest.find(content, "aboutClose")).doClick(0);
            assertTrue(closed[0]);
        });
    }

    @Test public void fileMenuIsGroupedWithHeadingsAndRestoreAfterOpen() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML);
            var labels = new java.util.ArrayList<String>();
            for (var component : panel.fileMenu().getComponents()) {
                if (component instanceof JLabel label) labels.add("# " + label.getText());
                else if (component instanceof JMenuItem item) labels.add(item.getText());
                else if (component instanceof JSeparator) labels.add("---");
            }
            assertEquals(java.util.List.of("# SÚBORY", "Nový rytmus", "Otvoriť YAML…", "Obnoviť posledný súbor", "Uložiť", "Uložiť ako…",
                    "---", "# POMOC", HelpDocuments.Document.GUIDE.title, HelpDocuments.Document.VISUAL_GUIDE.title, "O aplikácii"), labels);
        });
    }

    @Test public void editorMenuReplacesTheModeTogglesAndFoldsEveryRhythm() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML);
            var editor = (RSyntaxTextArea) NotePadTest.find(panel, "yamlEditor");
            var folds = editor.getFoldManager();
            assertNotNull(NotePadTest.find(panel, "editorMenuButton"));
            assertNull(NotePadTest.find(panel, "editMenuButton"));
            assertNull(NotePadTest.find(panel, "notesModeButton"));
            assertNull(NotePadTest.find(panel, "insertModeButton"));
            var labels = new java.util.ArrayList<String>();
            for (var component : panel.editMenu().getComponents()) {
                if (component instanceof JMenuItem item) labels.add(item.getText());
                else if (component instanceof JSeparator) labels.add("---");
            }
            assertEquals(java.util.List.of("Noty", "Prepisovanie", "---", "Rozbaliť všetko", "Zbaliť všetko"), labels);
            var items = panel.editMenu().getComponents();
            assertEquals(2, folds.getFoldCount());
            ((JMenuItem) items[3]).doClick(0);
            for (int i = 0; i < folds.getFoldCount(); i++) assertFalse(folds.getFold(i).isCollapsed());
            ((JMenuItem) items[4]).doClick(0);
            for (int i = 0; i < folds.getFoldCount(); i++) assertTrue(folds.getFold(i).isCollapsed());
        });
    }
}
