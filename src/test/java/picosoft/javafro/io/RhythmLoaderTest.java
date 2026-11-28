package picosoft.javafro.io;

import org.junit.Test;
import picosoft.javafro.base.Rhythm;
import picosoft.javafro.base.Track;
import picosoft.javafro.base.TimeSignature;
import picosoft.javafro.instruments.sample.matesamples.DundunInstrument;
import picosoft.javafro.instruments.sample.matesamples.SangbanInstrument;
import picosoft.javafro.instruments.sample.matesamples.KensereniInstrument;
import picosoft.javafro.sequencer.BeepSequencer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class RhythmLoaderTest {
    @Test public void rejectsRemovedUnderscoreBellNames() {
        for (String id : new String[]{"dundun_bell", "sangban_bell", "kensereni_bell", "kenken", "kenken_bell"}) {
            String source = "Test:\n  tracks: {" + id + ": 'x.......'}";
            assertThrows(IllegalArgumentException.class, () -> RhythmLoader.load(
                    new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))));
        }
    }

    @Test public void loadsBlankTemplateWithAllFifteenInstruments() throws Exception {
        var definitions = RhythmLoader.load(Path.of("rhythms/blankAllInstruments.yaml"));
        assertEquals(1, definitions.size());
        var rhythm = definitions.values().iterator().next();
        assertEquals(15, rhythm.tracks().size());
        for (Track track : rhythm.tracks()) assertEquals(java.util.Collections.nCopies(16, "."), picosoft.javafro.base.NotePattern.notes(track.pattern()));
    }

    private static final String YAML = """
            Djole:
              bpm: 116
              tracks:
                dundun_withBell: "D...DD.."
                sangban_withBell: "..S...S."
                kensereni_withBell: "K.K.K.K."
            """;

    private Rhythm load(String yaml) {
        return RhythmLoader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).get("Djole");
    }

    @Test
    public void loadsRhythmsAndInstruments() {
        Rhythm rhythm = load(YAML);
        assertEquals("Djole", rhythm.name());
        var renamed = RhythmLoader.load(new ByteArrayInputStream(YAML.replace("Djole:", "Kon_Masi:").getBytes(StandardCharsets.UTF_8)));
        assertEquals("Kon_Masi", renamed.get("Kon_Masi").name());
        assertEquals(116, rhythm.bpm());
        assertEquals(3, rhythm.tracks().size());
        assertEquals("..S...S.", rhythm.tracks().get(1).pattern());
        assertTrue(rhythm.tracks().get(0).instrument() instanceof DundunInstrument);
        assertTrue(rhythm.tracks().get(1).instrument() instanceof SangbanInstrument);
        assertTrue(rhythm.tracks().get(2).instrument() instanceof KensereniInstrument);
        var multiple = YAML + YAML.replace("Djole", "Second");
        assertEquals(2, RhythmLoader.load(new ByteArrayInputStream(multiple.getBytes(StandardCharsets.UTF_8))).size());
    }

    @Test
    public void loadsLongBaseAndVariationAndRejectsMalformedBars() {
        String bars = "D.......|".repeat(5000) + "D.......";
        String source = "Djole:\n  tracks:\n    dundun_withBell:\n      base: '" + bars
                + "'\n      variations: ['" + bars + "']\n";
        var track = load(source).tracks().getFirst();
        assertEquals(bars, track.pattern());
        assertEquals(bars, track.variations().getFirst());
        for (String invalid : new String[]{"", "|D.......", "D.......||D.......", bars + "|", bars + "!"}) {
            assertThrows(IllegalArgumentException.class, () -> load(YAML.replace("D...DD..", invalid)));
        }
    }

    @Test
    public void rejectsInvalidDefinitions() {
        for (String invalid : new String[]{
                YAML.replace("116", "0"), YAML.replace("116", "116.5"),
                YAML.replace("Djole:", "'':"),
                YAML.replace("dundun_withBell:", "unknown:"), YAML.replace("D...DD..", "D...s..."),
                YAML.replace("  bpm: 116", "  bpm: 116\n  bpm: 120"),
                YAML.replace("  bpm: 116", "  bpm: 116\n  humanize: 70"),
                YAML.replace("  bpm: 116", "  bpm: 116\n  name: Deprecated"),
                YAML.replace("116", "null"), YAML.replace("  tracks:", "  tracks: []\n  ignored:")
        }) {
            assertThrows(IllegalArgumentException.class, () -> load(invalid));
        }
        var error = assertThrows(IllegalArgumentException.class, () -> load(YAML.replace("dundun_withBell:", "unknown:")));
        assertTrue(error.getMessage().contains("Djole"));
        assertTrue(error.getMessage().contains("unknown"));
    }

    @Test
    public void selectsFirstOrRhythmAtCaretUsingYamlNodesAndUnicodeOffsets() {
        String first = "# 🥁 úvod\n\"Prvý rytmus\":\n  bpm: 90\n  stepsPerBeat: 4\n  tracks: {djembe: 'b.tt..s.'}\n";
        String second = "\n# medzi rytmami\n'Druhý: rytmus':\n  bpm: 140\n  timeSignature: '12/8'\n  tracks: {djembe: 'b..t..s.....'}\n";
        String source = first + second;
        assertEquals("Prvý rytmus", picosoft.javafro.ui.RhythmPlayerPanel.parseRhythm(source, 116).name());
        assertEquals("Prvý rytmus", RhythmLoader.atCaret(source, 0, 116).name());
        assertEquals("Prvý rytmus", RhythmLoader.atCaret(source, source.indexOf("# medzi"), 116).name());
        assertEquals("Druhý: rytmus", RhythmLoader.atCaret(source, source.indexOf("'Druhý"), 116).name());
        assertEquals(140, RhythmLoader.atCaret(source, source.length(), 116).bpm());
        assertEquals(3, RhythmLoader.atCaret(source, source.length(), 116).dialect().offsets().size());
        String flow = "{First: {tracks: {dundun_withBell: 'D.......'}}, Second: {tracks: {dundun_withBell: '....D...'}}}";
        assertEquals("Second", RhythmLoader.atCaret(flow, flow.indexOf("Second"), 116).name());
        // A duplicate breaks only the repeated rhythm.
        String duplicate = source + first;
        assertEquals("Prvý rytmus", RhythmLoader.atCaret(duplicate, 0, 116).name());
        assertEquals("Druhý: rytmus", RhythmLoader.atCaret(duplicate, duplicate.indexOf("'Druhý"), 116).name());
        assertThrows(IllegalArgumentException.class, () -> RhythmLoader.atCaret(duplicate, duplicate.lastIndexOf("\"Prvý"), 116));
    }

    private static final String GOOD = "Good:\n  tracks:\n    djembe: 'b.tt..s.'\n";

    private static java.util.List<String> lines(String source, java.util.List<int[]> ranges) {
        return ranges.stream().map(range -> source.substring(range[0], range[1])).toList();
    }

    @Test
    public void validatesEachRhythmOnItsOwnAndLocatesTheFaultyLine() {
        String source = GOOD + "Broken:\n  bpm: 100\n  tracks:\n    djembe: 'b.tt..s.'\n    dundun: 'x.......'\n" + GOOD.replace("Good", "Third");
        var entries = RhythmLoader.entries(source, 116);
        assertEquals(java.util.List.of("Good", "Broken", "Third"), entries.stream().map(RhythmLoader.Entry::id).toList());
        assertNotNull(entries.get(0).rhythm());
        assertNull(entries.get(1).rhythm());
        assertNotNull(entries.get(2).rhythm());
        assertTrue(entries.get(1).error().contains("Broken"));
        assertEquals(java.util.List.of("dundun: 'x.......'", "Broken:"), lines(source, entries.get(1).ranges()));
        assertEquals("Good", RhythmLoader.atCaret(source, 0, 116).name());
        assertEquals("Third", RhythmLoader.atCaret(source, source.indexOf("Third"), 116).name());
        assertThrows(IllegalArgumentException.class, () -> RhythmLoader.atCaret(source, source.indexOf("Broken"), 116));
        assertEquals(java.util.List.of("Good", "Third"), new ArrayList<>(RhythmLoader.valid(source, 116).keySet()));
    }

    @Test
    public void locatesBrokenVariationAndMetadataField() {
        String variation = "V:\n  tracks:\n    djembe:\n      base: 'b.tt..s.'\n      variations:\n        - 'b.tt..s.'\n        - 'b.tt..x.'\n";
        assertEquals(java.util.List.of("- 'b.tt..x.'", "V:"), lines(variation, RhythmLoader.entries(variation, 116).getFirst().ranges()));
        String bpm = "B:\n  bpm: -3\n  tracks:\n    djembe: 'b.tt..s.'\n";
        assertEquals(java.util.List.of("bpm: -3", "B:"), lines(bpm, RhythmLoader.entries(bpm, 116).getFirst().ranges()));
        String unknown = "U:\n  swing: 1\n  tracks:\n    djembe: 'b.tt..s.'\n";
        assertEquals(java.util.List.of("swing: 1", "U:"), lines(unknown, RhythmLoader.entries(unknown, 116).getFirst().ranges()));
    }

    @Test
    public void isolatesYamlSyntaxErrorsAndEmptyDocuments() {
        String syntax = GOOD + "Bad:\n  tracks:\n    djembe: 'b.tt..s.\n" + GOOD.replace("Good", "After");
        var entries = RhythmLoader.entries(syntax, 116);
        assertEquals(java.util.List.of("Good", "Bad", "After"), entries.stream().map(RhythmLoader.Entry::id).toList());
        assertNull(entries.get(1).rhythm());
        assertEquals("After", RhythmLoader.atCaret(syntax, syntax.indexOf("After"), 116).name());
        assertTrue(RhythmLoader.entries("- a\n- b\n", 116).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> RhythmLoader.atCaret("- a\n", 0, 116));
    }

    @Test
    public void missingTempoUsesGlobalDefaultAndMissingDialectUsesZeros() throws Exception {
        String minimal = "Test:\n  stepsPerBeat: 4\n  tracks:\n    djembe: 'b.tt..s.'\n";
        Rhythm rhythm = RhythmLoader.load(new ByteArrayInputStream(minimal.getBytes(StandardCharsets.UTF_8)), 93).get("Test");
        assertEquals(93, rhythm.bpm());
        assertEquals(java.util.List.of(0.0, 0.0, 0.0, 0.0), rhythm.dialect().offsets());
        assertEquals(93, picosoft.javafro.ui.RhythmPlayerPanel.parseRhythm(minimal, 93).bpm());
        assertEquals(picosoft.javafro.config.AppConfig.load().initialTempo(),
                RhythmLoader.load(new ByteArrayInputStream(minimal.getBytes(StandardCharsets.UTF_8))).get("Test").bpm());
        String explicit = minimal.replace("Test:", "Test:\n  bpm: 140\n  dialect: [0, -0.2, 0, 0]");
        rhythm = RhythmLoader.load(new ByteArrayInputStream(explicit.getBytes(StandardCharsets.UTF_8)), 93).get("Test");
        assertEquals(140, rhythm.bpm());
        assertEquals(java.util.List.of(0.0, -0.2, 0.0, 0.0), rhythm.dialect().offsets());
        String compound = minimal.replace("  stepsPerBeat: 4", "  timeSignature: '12/8'").replace("b.tt..s.", "b..t..s.....");
        rhythm = RhythmLoader.load(new ByteArrayInputStream(compound.getBytes(StandardCharsets.UTF_8)), 93).get("Test");
        assertEquals(java.util.List.of(0.0, 0.0, 0.0), rhythm.dialect().offsets());
    }

    @Test
    public void rhythmCopiesTracksAndValidatesTempo() {
        var tracks = new ArrayList<Track>(load(YAML).tracks());
        var rhythm = new Rhythm("Djole", tracks, 116);
        tracks.clear();
        assertEquals(3, rhythm.tracks().size());
        assertThrows(UnsupportedOperationException.class, () -> rhythm.tracks().clear());
        assertThrows(IllegalArgumentException.class, () -> new Rhythm("Djole", rhythm.tracks(), -1));
    }

    @Test
    public void playbackDelegatesRhythmSettings() throws Exception {
        var rhythm = load(YAML);
        var sequencer = new BeepSequencer();
        sequencer.stop(); // Validate without opening an audio device.
        sequencer.playRhythm(rhythm);
        sequencer.playRhythm(rhythm, 32);
        sequencer.playRhythm(rhythm, 32, 70);
        assertThrows(IllegalArgumentException.class, () -> sequencer.playRhythm(rhythm, 0, 70));
        assertThrows(IllegalArgumentException.class, () -> sequencer.playRhythm(rhythm, 32, 101));
        assertThrows(IllegalArgumentException.class, () -> sequencer.playRhythm((Rhythm) null));
    }

    @Test
    public void diskDjoleIsValid() throws Exception {
        Rhythm rhythm = RhythmLoader.load(Path.of("rhythms/imported/Djole.yaml")).get("Djole");
        var dundun = rhythm.tracks().stream().filter(track -> track.instrument() instanceof DundunInstrument)
                .findFirst().orElseThrow();
        assertEquals("D.x.x.x.D.D.x.x.", dundun.pattern());
        assertEquals(4, rhythm.stepsPerBeat());
        assertEquals(new TimeSignature(4, 4), rhythm.timeSignature());
    }

    @Test
    public void supportsTwoFourFourBarsOrOneEightFourBar() {
        String twoBars = YAML.replace("  bpm: 116", "  bpm: 116\n  timeSignature: '4/4'")
                .replace("D...DD..", "D...DD..|D...DD..");
        assertEquals("D...DD..|D...DD..", load(twoBars).tracks().getFirst().pattern());
        String oneLongBar = twoBars.replace("'4/4'", "'8/4'").replace("|", "")
                .replace("..S...S.", "..S...S...S...S.").replace("x.x.x.x.", "x.x.x.x.x.x.x.x.");
        Rhythm rhythm = load(oneLongBar);
        assertEquals(16, rhythm.timeSignature().stepsPerBar());
        assertEquals(2, rhythm.timeSignature().stepsPerBeat());
        assertEquals(16, rhythm.tracks().getFirst().pattern().length());
    }

    @Test
    public void supportsCompoundTwelveEightWithThreeStepsPerBeat() {
        String yaml = YAML.replace("  bpm: 116", "  bpm: 116\n  timeSignature: '12/8'")
                .replace("D...DD..", "D..D..D..D..")
                .replace("..S...S.", "..S..S..S..S")
                .replace("K.K.K.K.", "KKKKKKKKKKKK");
        Rhythm rhythm = load(yaml);
        assertEquals(12, rhythm.timeSignature().stepsPerBar());
        assertEquals(3, rhythm.timeSignature().stepsPerBeat());
        assertEquals(4, rhythm.timeSignature().stepsPerBar() / rhythm.timeSignature().stepsPerBeat());
    }

    @Test
    public void acceptsHalfBarsInBaseAndVariationsButRejectsOtherFractions() {
        var meter = new TimeSignature(4, 4);
        assertEquals("b.tt..s.b.tt..s.", meter.expandPattern("b.tt..s.", 4));
        assertEquals("x..x..x..x..", new TimeSignature(12, 8).expandPattern("x..x..", 3));
        assertThrows(IllegalArgumentException.class, () -> meter.validatePattern("x...", 4));
        assertThrows(IllegalArgumentException.class, () -> new TimeSignature(3, 4).validatePattern("x", 1));
        String yaml = YAML.replace("dundun_withBell: \"D...DD..\"", """
                dundun_withBell:
                      base: "x..."
                      variations:
                        - "xx.."
                """.stripTrailing());
        var track = load(yaml).tracks().getFirst();
        assertEquals("x...", track.pattern());
        assertEquals(java.util.List.of("xx.."), track.variations());
    }

    @Test
    public void rejectsInvalidMetersAndMisplacedBarLines() {
        for (String signature : new String[]{"0/4", "4/0", "4/3", "abc", "4", "999999999999/4"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> load(YAML.replace("  bpm: 116", "  bpm: 116\n  timeSignature: '" + signature + "'")));
        }
        for (String pattern : new String[]{"x..|xx...", "|D...DD..", "D...DD..|", "D...DD..||D...DD..", "D...DD..D...DD.."}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> load(YAML.replace("D...DD..", pattern)));
            assertTrue(error.getMessage().contains("Djole"));
            assertTrue(error.getMessage().contains("dundun_withBell"));
        }
    }

    @Test
    public void loadsStructuredTracksAndRejectsInvalidVariations() {
        String structured = YAML.replace("dundun_withBell: \"D...DD..\"", """
                dundun_withBell:
                      base: "D...DD.."
                      variations:
                        - "xx......"
                """.stripTrailing());
        var track = load(structured).tracks().getFirst();
        assertEquals("D...DD..", track.pattern());
        assertEquals(java.util.List.of("xx......"), track.variations());
        assertThrows(IllegalArgumentException.class, () -> load(structured.replace("xx......", "xx")));
        assertThrows(IllegalArgumentException.class, () -> load(structured.replace("variations:", "variationz:")));
        assertThrows(IllegalArgumentException.class, () -> load(structured.replace("variations:\n        - \"xx......\"", "variations: null")));
        assertThrows(IllegalArgumentException.class, () -> load(structured.replace("base:", "unknown:")));
        assertThrows(IllegalArgumentException.class, () -> load(YAML.replace("bpm: 116", "bpm: 116\n  stepsPerBeat: 0")));
    }
}
