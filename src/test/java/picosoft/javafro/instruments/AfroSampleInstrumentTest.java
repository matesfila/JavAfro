package picosoft.javafro.instruments;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import picosoft.javafro.base.Instrument;
import picosoft.javafro.instruments.sample.*;
import picosoft.javafro.instruments.sample.matesamples.*;
import picosoft.javafro.io.RhythmLoader;
import picosoft.javafro.config.AppConfig;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class AfroSampleInstrumentTest {
    private static class FixedRandom extends Random {
        final int variant;
        final double value;
        FixedRandom(int variant, double value) { this.variant = variant; this.value = value; }
        public int nextInt(int bound) { return variant % bound; }
        public double nextDouble() { return value; }
    }
    private FixedRandom fixed() { return new FixedRandom(0, .5); }
    private WavInstrument wav(String file) throws Exception {
        return new WavInstrument(getClass().getResource("/samples/" + file));
    }
    private double value(WavInstrument sample, long index, double rate, double gain) {
        double position = index * rate;
        long left = (long) position;
        double fraction = position - left;
        return (sample.sample(left) * (1 - fraction) + sample.sample(left + 1) * fraction) * gain;
    }

    private double configuredGain(String bank) throws Exception {
        try (var input = getClass().getResourceAsStream("/samples/afroplayer-samples.yaml")) {
            Map<?, ?> manifest = new Yaml().load(input);
            Map<?, ?> definition = (Map<?, ?>) ((Map<?, ?>) manifest.get("banks")).get(bank);
            return ((Number) definition.get("gain")).doubleValue();
        }
    }

    @Test public void sampleInstrumentsRequireAnExplicitNoteBeforeRenderingOrPlaying() {
        for (Instrument instrument : List.of(new DjembeInstrument(fixed()), new Djembe2Instrument(fixed()),
                new DundunInstrument(fixed()), new SangbanInstrument(fixed()), new KensereniInstrument(fixed()),
                new DundunBellInstrument(fixed()), new SangbanBellInstrument(fixed()), new KensereniBellInstrument(fixed()))) {
            assertThrows(UnsupportedOperationException.class, () -> instrument.sample(0));
            assertThrows(UnsupportedOperationException.class, () -> instrument.lengthSamples(1));
            // Reject before opening an audio line, even if audio hardware is unavailable.
            assertThrows(UnsupportedOperationException.class, () -> instrument.playTone(100));
        }
    }

    @Test public void allEightyThreeOriginalSamplesAreBundledAudibleAndKeepTheirTails() throws Exception {
        Map<?, ?> manifest;
        try (var input = getClass().getResourceAsStream("/samples/afroplayer-samples.yaml")) {
            manifest = new Yaml().load(input);
        }
        var files = new HashSet<String>();
        for (Object definition : ((Map<?, ?>) manifest.get("banks")).values()) {
            for (Object file : (List<?>) ((Map<?, ?>) definition).get("samples")) {
                assertTrue(files.add((String) file));
                WavInstrument sample = wav((String) file);
                long length = sample.lengthSamples(1);
                assertTrue(length > 100 && length < 44100 * 10);
                assertEquals(length, sample.lengthSamples(1_000_000));
                double energy = 0;
                for (long i = 0; i < length; i++) {
                    double amplitude = sample.sample(i);
                    assertTrue(Double.isFinite(amplitude));
                    energy += amplitude * amplitude;
                }
                assertTrue(file.toString(), energy > 0);
                assertEquals(0, sample.sample(-1), 0);
                assertEquals(0, sample.sample(length), 0);
            }
        }
        assertEquals(83, files.size());
    }

    @Test public void globalConfiguredOffsetsApplyToBankGainAndRateAndScaleWithHumanize() throws Exception {
        var config = AppConfig.parse(new ByteArrayInputStream(
                "playback: {humanizeDynamic: 0.2, humanizePitch: 0.05}".getBytes(StandardCharsets.UTF_8)), java.nio.file.Path.of("."));
        var instrument = new AfroSampleInstrument("djembe", new FixedRandom(0, 0), config) {};
        WavInstrument tone = wav("djembe/dj_tm_ton_v1.wav");
        double toneGain = configuredGain("dj_tone");
        for (int humanize : new int[]{0, 50, 100}) {
            double amount = humanize / 100.0;
            var voice = instrument.voice('T', humanize);
            assertEquals(value(tone, 500, 1 - .05 * amount, toneGain - .2 * amount), voice.sample(500), 1e-12);
        }
    }

    @Test public void drumHitMixesOriginalOpenOrClosedVoiceWithItsBellAtOriginalRates() throws Exception {
        var instrument = new DundunInstrument(fixed());
        var open = instrument.voice('X', 0);
        var closed = instrument.voice('i', 0);
        var bell = instrument.voice('b', 0);
        WavInstrument openSample = wav("afroplayer-dunduns/dundun_s2_v1.wav");
        WavInstrument closedSample = wav("afroplayer-dunduns/dundun_closed_v1.wav");
        WavInstrument bellSample = wav("afroplayer-dunduns/dundun_bell_open_v1.wav");
        for (long i : new long[]{0, 127, 500, 1001, 8000}) {
            double expectedBell = value(bellSample, i, .8, configuredGain("bell_dun"));
            assertEquals(expectedBell, bell.sample(i), 1e-12);
            assertEquals(value(openSample, i, .74, configuredGain("dundun_open")) + expectedBell, open.sample(i), 1e-12);
            assertEquals(value(closedSample, i, .9, configuredGain("dundun_closed")) + expectedBell, closed.sample(i), 1e-12);
        }
        assertEquals(Math.max((long) Math.ceil(openSample.lengthSamples(1) / .74),
                (long) Math.ceil(bellSample.lengthSamples(1) / .8)), open.lengthSamples(1));
        assertEquals(0, open.sample(open.lengthSamples(1)), 0);
        assertEquals(0, open.sample(-1), 0);
    }

    @Test public void originalProbabilitiesSkipTheWholeDrumAndBellHit() {
        for (var probability : Map.of('A', .12, 'B', .4, 'C', .8).entrySet()) {
            for (var factory : List.<java.util.function.Function<Random, Instrument>>of(
                    DundunInstrument::new, SangbanInstrument::new, KensereniInstrument::new)) {
                assertNotNull(factory.apply(new FixedRandom(0, probability.getValue() - .001)).voice(probability.getKey(), 0));
                assertNull(factory.apply(new FixedRandom(0, probability.getValue())).voice(probability.getKey(), 100));
                for (char note : "XxIib".toCharArray()) assertTrue(factory.apply(fixed()).supportsNote(note));
            }
        }
        var instrument = new SangbanInstrument(fixed());
        assertThrows(IllegalArgumentException.class, () -> instrument.voice('?', 0));
        assertThrows(IllegalArgumentException.class, () -> instrument.voice('X', 101));
    }

    @Test public void djembeTwoPreservesKontaktMappingPitchAndStableMultisamples() throws Exception {
        var instrument = new Djembe2Instrument(fixed());
        Map<Character, String> files = Map.of('D', "DJEMBEBASS2", 'o', "DJEMBE2", 'x', "DJEMBESLAP2", 'i', "DJEMBESLAP1");
        Map<Character, String> banks = Map.of('D', "dj2_bass", 'o', "dj2_tone", 'x', "dj2_slap", 'i', "dj2_closed");
        for (var entry : files.entrySet()) {
            Instrument voice = instrument.voice(entry.getKey(), 0);
            double rate = entry.getKey() == 'x' ? 1.1 : 1;
            WavInstrument sample = wav("djembe2/" + entry.getValue() + ".wav");
            assertEquals(value(sample, 500, rate, configuredGain(banks.get(entry.getKey()))), voice.sample(500), 1e-12);
            assertEquals((long) Math.ceil(sample.lengthSamples(1) / rate), voice.lengthSamples(1));
        }
        for (char note : "EpyFqz".toCharArray()) {
            assertNull(new Djembe2Instrument(new FixedRandom(0, .99)).voice(note, 0));
            assertNotNull(new Djembe2Instrument(new FixedRandom(0, 0)).voice(note, 0));
        }
        var human = new Djembe2Instrument(new FixedRandom(0, 0)).voice('D', 100);
        assertTrue(human.lengthSamples(1) > instrument.voice('D', 0).lengthSamples(1));
        double before = human.sample(500);
        instrument.voice('x', 100);
        assertEquals(before, human.sample(500), 0);
        var first = new Djembe2Instrument(new FixedRandom(0, .5)).voice('o', 0);
        var second = new Djembe2Instrument(new FixedRandom(1, .5)).voice('o', 0);
        boolean different = false;
        for (int i = 0; i < 5000; i++) if (first.sample(i) != second.sample(i)) { different = true; break; }
        assertTrue(different);
    }

    @Test public void yamlAcceptsAllNewInstrumentsAliasesAndOriginalSymbols() {
        String source = "Test:\n  stepsPerBeat: 4\n  tracks:\n"
                + "    dundun: 'X...i...b...A.BC'\n    sangban: 'X...I...b.......'\n"
                + "    kenken: 'X...i...b.......'\n    djembe2: 'D.o.x.i.EpyFqz..'\n"
                + "    dundun_bell: 'x...b...'\n    sangban_bell: 'x...b...'\n    kensereni_bell: 'x...b...'\n";
        var rhythm = RhythmLoader.load(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))).get("Test");
        assertEquals(7, rhythm.tracks().size());
        assertTrue(rhythm.tracks().get(0).instrument() instanceof DundunInstrument);
        assertTrue(rhythm.tracks().get(2).instrument() instanceof KensereniInstrument);
        assertTrue(rhythm.tracks().get(3).instrument() instanceof Djembe2Instrument);
        assertTrue(rhythm.tracks().get(4).instrument() instanceof DundunBellInstrument);
    }
}
