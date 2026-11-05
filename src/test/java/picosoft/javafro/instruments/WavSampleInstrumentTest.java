package picosoft.javafro.instruments;

import org.junit.Test;
import picosoft.javafro.base.Instrument;
import picosoft.javafro.instruments.sample.WavInstrument;
import picosoft.javafro.instruments.sample.matesamples.DundunInstrument;
import picosoft.javafro.instruments.sample.matesamples.KensereniInstrument;
import picosoft.javafro.instruments.sample.matesamples.SangbanInstrument;
import picosoft.javafro.io.RhythmLoader;
import javax.sound.sampled.AudioSystem;
import java.util.List;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class WavSampleInstrumentTest {
    @Test public void singleSampleAndSyntheticDrumsUseTrackSpecificLettersAndBellOnlyX() {
        List<Instrument> drums = List.of(
                new picosoft.javafro.instruments.sample.jimmijames.DundunInstrument(),
                new picosoft.javafro.instruments.sample.jimmijames.SangbanInstrument(),
                new picosoft.javafro.instruments.sample.jimmijames.KensereniInstrument(),
                new picosoft.javafro.instruments.synth.DundunInstrument(),
                new picosoft.javafro.instruments.synth.SangbanInstrument(),
                new picosoft.javafro.instruments.synth.KensereniInstrument());
        for (int index = 0; index < drums.size(); index++) {
            Instrument drum = drums.get(index);
            char stroke = "DSK".charAt(index % 3);
            for (char note : "DdSsKk".toCharArray()) assertEquals(Character.toUpperCase(note) == stroke, drum.supportsNote(note));
            assertNotNull(drum.voice(stroke, 0));
            assertNotNull(drum.voice(Character.toLowerCase(stroke), 0));
            assertTrue(drum.voice('x', 0).lengthSamples(1) > 100);
            assertFalse(drum.supportsNote('b'));
            assertFalse(drum.supportsNote('X'));
        }
    }

    @Test
    public void bundledSamplesAreAudibleFiniteAndPlayTheirWholeTails() {
        for (Instrument instrument : List.of(new picosoft.javafro.instruments.sample.jimmijames.DundunInstrument(), new picosoft.javafro.instruments.sample.jimmijames.SangbanInstrument(), new picosoft.javafro.instruments.sample.jimmijames.KensereniInstrument())) {
            long length = instrument.lengthSamples(1);
            assertTrue(length > 44100 && length < 44100 * 3);
            assertEquals(length, instrument.lengthSamples(5512));
            assertEquals(0, instrument.sample(-1), 0);
            assertEquals(0, instrument.sample(length), 0);
            assertEquals(0, instrument.sample(Long.MAX_VALUE), 0);
            double peak = 0;
            double energy = 0;
            for (long i = 0; i < length; i++) {
                double value = instrument.sample(i);
                assertTrue(Double.isFinite(value));
                peak = Math.max(peak, Math.abs(value));
                energy += value * value;
            }
            assertEquals(0.95, peak, 0.001);
            assertTrue(energy > 1);
        }
    }

    @Test
    public void sampleRhythmLoadsAndUsesRecordedInstruments() throws Exception {
        var rhythm = RhythmLoader.load(Path.of("rhythms/imported/Djole.yaml")).get("Djole");
        assertTrue(rhythm.tracks().stream().anyMatch(track -> track.instrument() instanceof DundunInstrument));
        assertTrue(rhythm.tracks().stream().anyMatch(track -> track.instrument() instanceof SangbanInstrument));
        assertTrue(rhythm.tracks().stream().anyMatch(track -> track.instrument() instanceof KensereniInstrument));
    }

    @Test
    public void wavResourceConstructorReadsUncompressedMonoPcm() throws Exception {
        var url = getClass().getResource("/samples/jimmyjames/sangban.wav");
        try (var stream = AudioSystem.getAudioInputStream(url)) {
            assertEquals(44100, stream.getFormat().getSampleRate(), 0);
            assertEquals(16, stream.getFormat().getSampleSizeInBits());
            assertEquals(1, stream.getFormat().getChannels());
            assertEquals(stream.getFrameLength(), new WavInstrument(url).lengthSamples(1));
        }
    }
}
