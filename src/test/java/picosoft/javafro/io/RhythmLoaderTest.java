package picosoft.javafro.io;

import org.junit.Test;
import picosoft.javafro.base.Rhythm;
import picosoft.javafro.base.Track;
import picosoft.javafro.base.TimeSignature;
import picosoft.javafro.instruments.synth.DundunInstrument;
import picosoft.javafro.instruments.synth.SangbanInstrument;
import picosoft.javafro.sequencer.BeepSequencer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class RhythmLoaderTest {
    private static final String YAML = """
            Djole:
              name: "Djole"
              bpm: 116
              tracks:
                dundun: "x...xx.."
                sangban: "..x...x."
                kensereni: "x.x.x.x."
            """;

    private Rhythm load(String yaml) {
        return RhythmLoader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).get("Djole");
    }

    @Test
    public void loadsRhythmsAndInstruments() {
        Rhythm rhythm = load(YAML);
        assertEquals("Djole", rhythm.name());
        assertEquals(116, rhythm.bpm());
        assertEquals(3, rhythm.tracks().size());
        assertEquals("..x...x.", rhythm.tracks().get(1).pattern());
        assertTrue(rhythm.tracks().get(0).instrument() instanceof DundunInstrument);
        assertTrue(rhythm.tracks().get(1).instrument() instanceof SangbanInstrument);
        var multiple = YAML + YAML.replace("Djole", "Second");
        assertEquals(2, RhythmLoader.load(new ByteArrayInputStream(multiple.getBytes(StandardCharsets.UTF_8))).size());
    }

    @Test
    public void rejectsInvalidDefinitions() {
        for (String invalid : new String[]{
                YAML.replace("116", "0"), YAML.replace("116", "116.5"),
                YAML.replace("name: \"Djole\"", "name: \"\""),
                YAML.replace("dundun:", "unknown:"), YAML.replace("x...xx..", "x...s..."),
                YAML.replace("  bpm: 116", "  bpm: 116\n  bpm: 120"),
                YAML.replace("  bpm: 116", "  bpm: 116\n  humanize: 70"),
                YAML.replace("  bpm: 116\n", ""), YAML.replace("  tracks:", "  tracks: []\n  ignored:")
        }) {
            assertThrows(IllegalArgumentException.class, () -> load(invalid));
        }
        var error = assertThrows(IllegalArgumentException.class, () -> load(YAML.replace("dundun:", "unknown:")));
        assertTrue(error.getMessage().contains("Djole"));
        assertTrue(error.getMessage().contains("unknown"));
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
        sequencer.playPattern(rhythm);
        sequencer.playPattern(rhythm, 32);
        sequencer.playPattern(rhythm, 32, 70);
        assertThrows(IllegalArgumentException.class, () -> sequencer.playPattern(rhythm, 0, 70));
        assertThrows(IllegalArgumentException.class, () -> sequencer.playPattern(rhythm, 32, 101));
        assertThrows(IllegalArgumentException.class, () -> sequencer.playPattern((Rhythm) null));
    }

    @Test
    public void bundledDjoleIsValid() throws Exception {
        try (var input = getClass().getResourceAsStream("/rhythms/djole.yaml")) {
            Rhythm rhythm = RhythmLoader.load(input).get("djole");
            assertEquals("x.......x.x.....", rhythm.tracks().getFirst().pattern());
            assertEquals(4, rhythm.stepsPerBeat());
            assertEquals(new TimeSignature(4, 4), rhythm.timeSignature());
        }
    }

    @Test
    public void supportsTwoFourFourBarsOrOneEightFourBar() {
        String twoBars = YAML.replace("  bpm: 116", "  bpm: 116\n  timeSignature: '4/4'")
                .replace("x...xx..", "x...xx..|x...xx..");
        assertEquals("x...xx..|x...xx..", load(twoBars).tracks().getFirst().pattern());
        String oneLongBar = twoBars.replace("'4/4'", "'8/4'").replace("|", "")
                .replace("..x...x.", "..x...x...x...x.").replace("x.x.x.x.", "x.x.x.x.x.x.x.x.");
        Rhythm rhythm = load(oneLongBar);
        assertEquals(16, rhythm.timeSignature().stepsPerBar());
        assertEquals(2, rhythm.timeSignature().stepsPerBeat());
        assertEquals(16, rhythm.tracks().getFirst().pattern().length());
    }

    @Test
    public void supportsCompoundTwelveEightWithThreeStepsPerBeat() {
        String yaml = YAML.replace("  bpm: 116", "  bpm: 116\n  timeSignature: '12/8'")
                .replace("x...xx..", "x..x..x..x..")
                .replace("..x...x.", "..x..x..x..x")
                .replace("x.x.x.x.", "xxxxxxxxxxxx");
        Rhythm rhythm = load(yaml);
        assertEquals(12, rhythm.timeSignature().stepsPerBar());
        assertEquals(3, rhythm.timeSignature().stepsPerBeat());
        assertEquals(4, rhythm.timeSignature().stepsPerBar() / rhythm.timeSignature().stepsPerBeat());
    }

    @Test
    public void rejectsInvalidMetersAndMisplacedBarLines() {
        for (String signature : new String[]{"0/4", "4/0", "4/3", "abc", "4", "999999999999/4"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> load(YAML.replace("  bpm: 116", "  bpm: 116\n  timeSignature: '" + signature + "'")));
        }
        for (String pattern : new String[]{"x...|xx..", "|x...xx..", "x...xx..|", "x...xx..||x...xx..", "x...xx..x...xx.."}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> load(YAML.replace("x...xx..", pattern)));
            assertTrue(error.getMessage().contains("Djole"));
            assertTrue(error.getMessage().contains("dundun"));
        }
    }

    @Test
    public void loadsStructuredTracksAndRejectsInvalidVariations() {
        String structured = YAML.replace("dundun: \"x...xx..\"", """
                dundun:
                      base: "x...xx.."
                      variations:
                        - "xx......"
                """.stripTrailing());
        var track = load(structured).tracks().getFirst();
        assertEquals("x...xx..", track.pattern());
        assertEquals(java.util.List.of("xx......"), track.variations());
        assertThrows(IllegalArgumentException.class, () -> load(structured.replace("xx......", "xx")));
        assertThrows(IllegalArgumentException.class, () -> load(structured.replace("variations:", "variationz:")));
        assertThrows(IllegalArgumentException.class, () -> load(structured.replace("variations:\n        - \"xx......\"", "variations: null")));
        assertThrows(IllegalArgumentException.class, () -> load(structured.replace("base:", "unknown:")));
        assertThrows(IllegalArgumentException.class, () -> load(YAML.replace("bpm: 116", "bpm: 116\n  stepsPerBeat: 0")));
    }
}
