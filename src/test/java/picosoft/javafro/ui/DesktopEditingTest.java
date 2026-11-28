package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import picosoft.javafro.io.DesktopDraftStore;
import picosoft.javafro.io.RhythmLoader;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class DesktopEditingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String YAML = "Test:\n  bpm: 120\n  stepsPerBeat: 4\n  tracks: {djembe: 'b.tt..s.'}\n";

    @Test public void dialDoesNotJumpOnContactAndNumericEntryCanSetExactBpm() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML);
            JSlider dial = (JSlider) find(panel, "tempoSlider"); dial.setSize(80, 80);
            dial.dispatchEvent(new MouseEvent(dial, MouseEvent.MOUSE_PRESSED, 1, MouseEvent.BUTTON1_DOWN_MASK, 40, 40, 1, false, MouseEvent.BUTTON1));
            assertEquals(120, dial.getValue());
            dial.dispatchEvent(new MouseEvent(dial, MouseEvent.MOUSE_DRAGGED, 2, MouseEvent.BUTTON1_DOWN_MASK, 40, 36, 0, false, MouseEvent.NOBUTTON));
            assertEquals(121, dial.getValue());
            dial.dispatchEvent(new MouseEvent(dial, MouseEvent.MOUSE_RELEASED, 3, 0, 40, 36, 1, false, MouseEvent.BUTTON1));
            JSpinner number = (JSpinner) find(panel, "tempoNumber");
            ((JSpinner.DefaultEditor) number.getEditor()).getTextField().setText("143");
            assertEquals(143, dial.getValue());
            ((JSpinner) find(panel, "tempoNumber")).setValue(10);
            assertEquals(10, dial.getValue());
        });
    }

    @Test public void restoresInvalidDraftAndSavesItVerbatim() throws Exception {
        var directory = temporary.newFolder("drafts").toPath();
        var target = temporary.getRoot().toPath().resolve("invalid.yaml");
        String invalid = "Test: [\né";
        var store = new DesktopDraftStore(directory);
        store.save(new DesktopDraftStore.Draft(invalid, "", 5, YAML, null), false);
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(""); panel.enableDraftRecovery(directory, null);
            var editor = (RSyntaxTextArea) find(panel, "yamlEditor");
            assertEquals(invalid, editor.getText()); assertEquals(5, editor.getCaretPosition());
            assertTrue(panel.snapshotDraft().dirty());
            try { panel.saveTo(target); } catch (Exception exception) { throw new AssertionError(exception); }
            assertFalse(panel.snapshotDraft().dirty());
            panel.flushDraft();
        });
        assertEquals(invalid, Files.readString(target));
    }

    @Test public void restoresLiveTempoAndExportsItIntoTheSavedDocument() throws Exception {
        var directory = temporary.newFolder("controls").toPath();
        var target = temporary.getRoot().toPath().resolve("saved.yaml");
        var store = new DesktopDraftStore(directory);
        store.save(new DesktopDraftStore.Draft(YAML, "", 0, YAML,
                new DesktopDraftStore.Controls("Test", 143, List.of(0., -.2, 0., .2))), false);
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(""); panel.enableDraftRecovery(directory, null);
            assertEquals(143, ((JSlider) find(panel, "tempoSlider")).getValue());
            assertTrue(panel.snapshotDraft().dirty());
            try { panel.saveTo(target); } catch (Exception exception) { throw new AssertionError(exception); }
            assertFalse(panel.snapshotDraft().dirty()); panel.flushDraft();
        });
        assertEquals(143, RhythmLoader.load(target).get("Test").bpm());
        assertEquals(List.of(0., -.2, 0., .2), RhythmLoader.load(target).get("Test").dialect().offsets());
    }

    @Test public void cancelledReplacementOrFailedSaveNeverApprovesDiscard() {
        var called = new AtomicBoolean();
        assertFalse(DocumentGuard.approve(true, () -> DocumentGuard.Decision.CANCEL, () -> { called.set(true); return true; }));
        assertFalse(called.get());
        assertFalse(DocumentGuard.approve(true, () -> DocumentGuard.Decision.SAVE, () -> false));
        assertTrue(DocumentGuard.approve(true, () -> DocumentGuard.Decision.SAVE, () -> true));
        assertTrue(DocumentGuard.approve(true, () -> DocumentGuard.Decision.DISCARD, () -> false));
    }

    @Test public void flushPreservesRawEditsAndCaretWithoutSavingToAFile() throws Exception {
        var directory = temporary.newFolder("autosave").toPath();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML); panel.enableDraftRecovery(directory, null);
            var editor = (RSyntaxTextArea) find(panel, "yamlEditor");
            editor.setText("unfinished: ["); editor.setCaretPosition(5); panel.flushDraft();
        });
        var restored = new DesktopDraftStore(directory).current();
        assertEquals("unfinished: [", restored.source()); assertEquals(5, restored.caret());
        assertEquals(YAML, restored.savedSource()); assertTrue(restored.dirty());
    }

    @Test public void closingWithoutSavingLeavesACleanDraftForTheNextStart() throws Exception {
        var directory = temporary.newFolder("discard").toPath();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML); panel.enableDraftRecovery(directory, null);
            var editor = (RSyntaxTextArea) find(panel, "yamlEditor");
            editor.setText("declined: ["); editor.setCaretPosition(9); panel.flushDraft();
            panel.discardDraftChanges();
        });
        var restored = new DesktopDraftStore(directory).current();
        assertEquals(YAML, restored.source()); assertFalse(restored.dirty());
        SwingUtilities.invokeAndWait(() -> {
            var next = new RhythmPlayerPanel(""); next.enableDraftRecovery(directory, null);
            assertFalse(next.snapshotDraft().dirty());
        });
    }

    @Test public void aConflictingWindowShowsAnErrorAndKeepsBothVersionsSafe() throws Exception {
        var directory = temporary.newFolder("conflict").toPath();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel(YAML); panel.enableDraftRecovery(directory, null);
            var editor = (RSyntaxTextArea) find(panel, "yamlEditor");
            editor.setText("this window: [");
            try {
                var other = new DesktopDraftStore(directory);
                other.save(new DesktopDraftStore.Draft("newer window: [", "", 0, "", null), false);
            } catch (Exception exception) { throw new AssertionError(exception); }
            panel.flushDraft();
            assertEquals("this window: [", editor.getText());
            var status = (HeaderStatus) find(panel, "headerStatus");
            assertTrue(status.message(HeaderStatus.Slot.DRAFT).contains("nepodarilo"));
            assertTrue(status.isInvalid());
        });
        assertEquals("newer window: [", new DesktopDraftStore(directory).current().source());
    }

    private static Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) { Component found = find(nested, name); if (found != null) return found; }
        }
        return null;
    }
}
