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
    private static final String YAML = """
            Djole:
              bpm: 116
              tracks:
                dundun: "D...DD.."
                sangban: "..S...S."
                kensereni: "K.K.K.K."
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
    public void rejectsInvalidDefinitions() {
        for (String invalid : new String[]{
                YAML.replace("116", "0"), YAML.replace("116", "116.5"),
                YAML.replace("Djole:", "'':"),
                YAML.replace("dundun:", "unknown:"), YAML.replace("D...DD..", "D...s..."),
                YAML.replace("  bpm: 116", "  bpm: 116\n  bpm: 120"),
                YAML.replace("  bpm: 116", "  bpm: 116\n  humanize: 70"),
                YAML.replace("  bpm: 116", "  bpm: 116\n  name: Deprecated"),
                YAML.replace("116", "null"), YAML.replace("  tracks:", "  tracks: []\n  ignored:")
        }) {
            assertThrows(IllegalArgumentException.class, () -> load(invalid));
        }
        var error = assertThrows(IllegalArgumentException.class, () -> load(YAML.replace("dundun:", "unknown:")));
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
        String flow = "{First: {tracks: {dundun: 'D.......'}}, Second: {tracks: {dundun: '....D...'}}}";
        assertEquals("Second", RhythmLoader.atCaret(flow, flow.indexOf("Second"), 116).name());
        assertThrows(IllegalArgumentException.class, () -> RhythmLoader.atCaret(source + first, 0, 116));
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
        String yaml = YAML.replace("dundun: \"D...DD..\"", """
                dundun:
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
            assertTrue(error.getMessage().contains("dundun"));
        }
    }

    @Test
    public void loadsStructuredTracksAndRejectsInvalidVariations() {
        String structured = YAML.replace("dundun: \"D...DD..\"", """
                dundun:
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
