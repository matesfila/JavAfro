package picosoft.javafro.sequencer;

import org.junit.Test;
import picosoft.javafro.base.*;
import picosoft.javafro.instruments.sample.matesamples.DjembeInstrument;
import picosoft.javafro.io.RhythmLoader;

import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import static org.junit.Assert.*;

public class DjembeInstrumentTest {
    private static class FixedRandom extends Random {
        private final int variant;
        private final double value;
        FixedRandom(int variant, double value) { this.variant = variant; this.value = value; }
        @Override public int nextInt(int bound) { return variant % bound; }
        @Override public double nextDouble() { return value; }
    }
    private double energy(Instrument voice) {
        double sum = 0;
        long length = voice.lengthSamples(1);
        assertTrue(length > 1000 && length < 44100 * 3);
        for (long i = 0; i < length; i++) {
            double sample = voice.sample(i);
            assertTrue(Double.isFinite(sample));
            // Instrument gain can exceed one; the mixer applies its output gain and clipping.
            sum += sample * sample;
        }
        assertEquals(0, voice.sample(-1), 0);
        assertEquals(0, voice.sample(length), 0);
        return sum;
    }
    @Test public void allTwentyNineVariantsAreAudibleAndKeepTheirFullTails() {
        Map<Character, Integer> banks = Map.of('D', 1, 'T', 6, 'S', 8, 'I', 3, 'C', 3, 'A', 8);
        for (var bank : banks.entrySet()) {
            Set<Double> fingerprints = new HashSet<>();
            for (int variant = 0; variant < bank.getValue(); variant++) {
                var voice = new DjembeInstrument(new FixedRandom(variant, .5)).voice(bank.getKey(), 0);
                double energy = energy(voice);
                assertTrue(energy > .01);
                fingerprints.add(energy);
                assertEquals(voice.lengthSamples(1), voice.lengthSamples(5512));
            }
            assertEquals(bank.getValue().intValue(), fingerprints.size());
        }
    }
    @Test public void accentsAndHumanizationChangeDynamicsAndPitchWithoutMutatingVoices() {
        for (char note : new char[]{'d', 't', 's'}) {
            var normal = new DjembeInstrument(new FixedRandom(0, .5)).voice(note, 0);
            var accent = new DjembeInstrument(new FixedRandom(0, .5)).voice(Character.toUpperCase(note), 0);
            assertEquals(.49, energy(normal) / energy(accent), 1e-10);
        }
        var instrument = new DjembeInstrument(new FixedRandom(0, 0));
        var straight = instrument.voice('D', 0);
        var human = instrument.voice('D', 100);
        assertTrue(human.lengthSamples(1) > straight.lengthSamples(1));
        double before = human.sample(500);
        instrument.voice('S', 100);
        assertEquals(before, human.sample(500), 0);
        assertThrows(IllegalArgumentException.class, () -> instrument.voice('?', 0));
        assertThrows(IllegalArgumentException.class, () -> instrument.voice('D', 101));
    }
    @Test public void oldProbabilitySymbolsRemainCompatible() {
        var play = new DjembeInstrument(new FixedRandom(0, 0));
        var skip = new DjembeInstrument(new FixedRandom(0, .99));
        for (char note : "EpyFqz".toCharArray()) {
            assertNotNull(play.voice(note, 0));
            assertNull(skip.voice(note, 100));
        }
        for (char note : "Doxi".toCharArray()) assertNotNull(skip.voice(note, 0));
        assertThrows(IllegalArgumentException.class, () -> new BeepTrack("d.......", new picosoft.javafro.base.BeepInstrument()));
    }
    @Test public void loaderAndSequencerPlayNoteSpecificVoicesAtTheExistingPulseTimes() throws Exception {
        var demo = RhythmLoader.load(Path.of("rhythms/djembe-demo.yaml")).get("djembe-demo");
        assertTrue(demo.tracks().getLast().instrument() instanceof DjembeInstrument);
        List<Character> played = new ArrayList<>();
        Instrument instrument = new Instrument() {
            public double sample(long i) { return 0; }
            public boolean supportsNote(char note) { return "dts".indexOf(note) >= 0; }
            public Instrument voice(char note, int humanize) {
                played.add(note);
                return new Instrument() {
                    public double sample(long i) { return .25; }
                    public long lengthSamples(long pulse) { return 1; }
                };
            }
        };
        var pcm = new ByteArrayOutputStream();
        var line = (SourceDataLine) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SourceDataLine.class},
                (proxy, method, args) -> { if (method.getName().equals("write")) { pcm.write((byte[]) args[0], (int) args[1], (int) args[2]); return args[2]; } return null; });
        new BeepSequencer(format -> line).playRhythm(new Rhythm("Notes", List.of(new BeepTrack("d.t.s...", instrument)), 120), 1, 0);
        assertEquals(List.of('d', 't', 's'), played);
        List<Integer> onsets = new ArrayList<>();
        byte[] audio = pcm.toByteArray();
        for (int i = 0; i < audio.length; i += 2) if (audio[i] != 0 || audio[i + 1] != 0) onsets.add(i / 2);
        assertEquals(List.of(0, 22050, 44100), onsets);
    }
}
