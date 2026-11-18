package picosoft.javafro.instruments;

import org.junit.Test;
import picosoft.javafro.instruments.sample.matesamples.BalafonInstrument;
import picosoft.javafro.instruments.sample.WavSampleInstrument;
import picosoft.javafro.io.RhythmLoader;
import picosoft.javafro.base.NotePattern;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

public class BalafonInstrumentTest {
    @Test public void allPitchesSelectTheirThreeOriginalRecordingsWithoutPitchOrGainChanges() {
        for (String pitch : List.of("g0", "a0", "h0", "c1", "d1", "e1", "f1")) {
            for (int variant = 1; variant <= 3; variant++) {
                int index = variant - 1;
                var instrument = new BalafonInstrument(new Random() {
                    @Override public int nextInt(int bound) { assertEquals(3, bound); return index; }
                });
                assertTrue(instrument.supportsNote(pitch));
                var voice = instrument.voice(pitch, 0);
                var original = WavSampleInstrument.loadSample("/samples/balafon/balafamer_" + pitch + "_dyn2_ver" + variant + ".wav");
                assertEquals(original.lengthSamples(1), voice.lengthSamples(1));
                for (long i = 0; i < voice.lengthSamples(1); i += 97) assertEquals(original.sample(i), voice.sample(i), 0);
            }
        }
        var instrument = new BalafonInstrument();
        for (String invalid : List.of("g", "G0", "b0", "c2", "x")) {
            assertFalse(instrument.supportsNote(invalid));
            assertThrows(IllegalArgumentException.class, () -> instrument.voice(invalid, 0));
        }
    }

    @Test public void loadsOriginalMaanePartAndDemo() throws Exception {
        assertEquals(List.of("g0", ".", "c1", "."), NotePattern.notes("g0 .. c1 ."));
        for (String file : List.of("rhythms/balafon-demo.yaml", "rhythms/imported/Maane.yaml")) {
            var rhythm = RhythmLoader.load(Path.of(file)).values().iterator().next();
            var track = rhythm.tracks().stream().filter(t -> t.instrumentId().equals("balafon")).findFirst().orElseThrow();
            assertTrue(track.instrument() instanceof BalafonInstrument);
            assertEquals(List.of("c1", "e1", "g0", "c1", "g0", "c1", "c1", "e1", "a0", "d1", "a0", "d1"), NotePattern.notes(track.pattern()));
        }
    }
}
