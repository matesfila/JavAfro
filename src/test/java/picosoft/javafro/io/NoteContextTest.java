package picosoft.javafro.io;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.*;

import static org.junit.Assert.*;

/** Desktop counterpart of web/src/note-context.test.ts (note keyboard context and note lists). */
public class NoteContextTest {
    private static String at(String source) {
        int caret = source.indexOf('¦');
        return RhythmLoader.instrumentAtCaret(source.replace("¦", ""), caret);
    }

    @Test public void followsTheActiveTrackIncludingQuotedKeysAndEmptyValues() {
        for (String instrument : RhythmLoader.INSTRUMENTS) {
            assertEquals(instrument, at("demo:\n  tracks:\n    '" + instrument + "': \"..¦..\"\n    djembe: \"....\""));
            assertEquals(instrument, at("demo:\n  tracks:\n  ¦  " + instrument + ":"));
        }
    }

    @Test public void keepsTrackContextInBaseVariationsAndMultilinePatterns() {
        for (String pattern : new String[]{"base: \"..¦..\"", "variations:\n        - \"..¦..\"", "base: |\n        ..¦.."}) {
            assertEquals("dundun_withBell",
                    at("demo:\n  tracks:\n    dundun_withBell:\n      " + pattern + "\n    sangban_withBell: \"....\""));
        }
    }

    @Test public void worksDuringInvalidEditsAndOnFlowMapLines() {
        assertEquals("dundun_withBell", at("demo:\n  tracks:\n    dundun_withBell: \"D?¦\""));
        assertEquals("sangban_withBell", at("demo:\n  tracks: {dundun_withBell: \"DD\", sangban_withBell: \"S¦S\"}"));
        assertEquals("dundun_withBell", at("demo:\n  tracks:\n    dundun_withBell: \"DD\"  ¦ # comment"));
        assertEquals("djembe", at("\"Ŕytmus\":\n  tracks:\n    djembe: \"b.¦t\""));
    }

    @Test public void hidesNotesOutsideKnownTracks() {
        for (String source : new String[]{
                "demo:\n  bpm: 1¦00\n  tracks:\n    dundun_withBell: \"DD\"",
                "demo:\n  tracks:\n    unknown: \"¦DD\"",
                "demo:\n  legacy:\n    dundun_withBell: \"¦DD\"",
                "demo:\n  tracks:\n    dundun_withBell: \"DD\"\n  bpm: ¦100",
                "demo: [¦"}) {
            assertNull(source, at(source));
        }
        assertEquals(List.of(), RhythmLoader.notesFor(null));
        assertEquals(List.of(), RhythmLoader.notesFor("unknown"));
    }

    @Test public void listsManifestNotesInManifestOrder() throws Exception {
        Map<?, ?> instruments;
        try (InputStream input = getClass().getResourceAsStream("/samples/afroplayer-samples.yaml")) {
            instruments = (Map<?, ?>) ((Map<?, ?>) new Yaml().load(input)).get("instruments");
        }
        for (String instrument : RhythmLoader.INSTRUMENTS) {
            Map<?, ?> definition = (Map<?, ?>) instruments.get(instrument);
            if (definition != null) {
                assertEquals(instrument, new ArrayList<>(((Map<?, ?>) definition.get("notes")).keySet()), RhythmLoader.notesFor(instrument));
            }
        }
        assertEquals(List.of("D", "d", "x"), RhythmLoader.notesFor("dundun_freesound"));
    }

    @Test public void offersEverySupportedNoteExactlyOnce() {
        List<String> candidates = new ArrayList<>(List.of("g0", "a0", "h0", "c1", "d1", "e1", "f1"));
        for (char letter = 'A'; letter <= 'Z'; letter++) { candidates.add(String.valueOf(letter)); candidates.add(String.valueOf(Character.toLowerCase(letter))); }
        for (String id : RhythmLoader.INSTRUMENTS) {
            var instrument = RhythmLoader.load(new java.io.ByteArrayInputStream(
                    ("t:\n  tracks: {" + id + ": '........'}").getBytes()), 120)
                    .get("t").tracks().getFirst().instrument();
            List<String> notes = RhythmLoader.notesFor(id);
            assertEquals(id, notes.size(), new HashSet<>(notes).size());
            assertEquals(id, new HashSet<>(candidates.stream().filter(instrument::supportsNote).toList()), new HashSet<>(notes));
            assertEquals(id, instrument.notes(), notes);
        }
    }
}
