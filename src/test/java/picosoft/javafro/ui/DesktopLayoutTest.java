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

    @Test public void mixerAndLiveControlsAreInlineAndInvalidYamlIsReportedUnderTheEditor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML);
            var mixer = NotePadTest.find(panel, "masterVolume");
            assertNotNull(mixer);
            assertTrue(SwingUtilities.isDescendingFrom(mixer, panel));
            assertNull(SwingUtilities.getAncestorOfClass(JDialog.class, mixer));
            assertNotNull(NotePadTest.find(panel, "trackSolo-0"));
            var status = (JLabel) NotePadTest.find(panel, "editorStatus");
            assertFalse(status.isVisible());
            var editor = (RSyntaxTextArea) NotePadTest.find(panel, "yamlEditor");
            editor.insert("Broken: [\n", 0);
            editor.setCaretPosition(3);
            assertTrue(status.isVisible());
            assertTrue(status.getText().startsWith("Neplatný YAML"));
            assertEquals("", ((JLabel) NotePadTest.find(panel, "playbackStatus")).getText());
        });
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

    @Test public void editMenuCollapsesAndExpandsEveryRhythm() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML);
            var editor = (RSyntaxTextArea) NotePadTest.find(panel, "yamlEditor");
            var folds = editor.getFoldManager();
            var items = panel.editMenu().getComponents();
            assertEquals("Zbaliť všetko", ((JMenuItem) items[0]).getText());
            assertEquals("Rozbaliť všetko", ((JMenuItem) items[1]).getText());
            assertEquals(2, folds.getFoldCount());
            ((JMenuItem) items[1]).doClick(0);
            for (int i = 0; i < folds.getFoldCount(); i++) assertFalse(folds.getFold(i).isCollapsed());
            ((JMenuItem) items[0]).doClick(0);
            for (int i = 0; i < folds.getFoldCount(); i++) assertTrue(folds.getFold(i).isCollapsed());
        });
    }
}
