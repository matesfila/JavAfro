package picosoft.javafro.io;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

public class RhythmExportTest {
    private static final DesktopDraftStore.Controls CONTROLS = new DesktopDraftStore.Controls("First", 143, List.of(0., .2, 0., -.2));
    @Test public void exportsLiveControlsAndPreservesCommentsOtherRhythmsAndLegacy() {
        String source = "# My rhythms\nFirst:\n  bpm: 90 # keep tempo comment\n  stepsPerBeat: 4\n  tracks: {djembe: 'b.tt..s.'}\n  legacy: {marker: keep}\nSecond:\n  bpm: 120\n  tracks: {dundun_withBell: 'D.......'}\n";
        String exported = RhythmExport.export(source, CONTROLS, 116);
        var rhythms = RhythmLoader.load(new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)), 116);
        assertEquals(143, rhythms.get("First").bpm());
        assertEquals(CONTROLS.dialect(), rhythms.get("First").dialect().offsets());
        assertEquals(120, rhythms.get("Second").bpm());
        assertTrue(exported.contains("# My rhythms"));
        assertTrue(exported.contains("# keep tempo comment"));
        assertTrue(exported.contains("marker: keep"));
    }
    @Test public void exportsControlsOfAValidRhythmNextToABrokenOne() {
        String source = "First:\n  stepsPerBeat: 4\n  tracks: {djembe: 'b.tt..s.'}\nBroken:\n  tracks: {djembe: 'b.tt..x.'}\n";
        String exported = RhythmExport.export(source, CONTROLS, 116);
        assertTrue(exported.contains("bpm: 143"));
        assertTrue(exported.contains("b.tt..x."));
    }
    @Test public void invalidOrEmptyWorkIsSavedVerbatim() {
        for (String source : List.of("First: [", "", "# rozpracovaný rytmus\nFirst: {bpm: -1}\n")) {
            assertEquals(source, RhythmExport.export(source, CONTROLS, 116));
        }
    }
    @Test public void untouchedDocumentsKeepExactFormatting() {
        String source = "First:\n  tracks: {djembe: 'b.tt..s.'}\n";
        assertEquals(source, RhythmExport.export(source, new DesktopDraftStore.Controls("First", 116, List.of(0., 0.)), 116));
        assertEquals(source, RhythmExport.export(source, null, 116));
        assertEquals(source, RhythmExport.export(source, new DesktopDraftStore.Controls("missing", 120, List.of(0., 0.)), 116));
    }
    @Test public void exportsOnlyTheControlThatChanged() {
        String source = "First:\n  tracks: {djembe: 'b.tt..s.'}\n";
        String tempo = RhythmExport.export(source, new DesktopDraftStore.Controls("First", 140, List.of(0., 0.)), 116);
        assertTrue(tempo.contains("bpm: 140"));
        assertFalse(tempo.contains("dialect:"));
        String dialect = RhythmExport.export(source, new DesktopDraftStore.Controls("First", 116, List.of(0., -.2)), 116);
        assertTrue(dialect.contains("dialect: [0.0, -0.2]"));
        assertFalse(dialect.contains("bpm:"));
    }
}
