package picosoft.javafro.sequencer;

import org.junit.Test;
import picosoft.javafro.base.*;
import picosoft.javafro.io.RhythmLoader;
import javax.sound.sampled.SourceDataLine;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class DialectPlaybackTest {
    private static SourceDataLine sink(ByteArrayOutputStream pcm) {
        return (SourceDataLine) Proxy.newProxyInstance(DialectPlaybackTest.class.getClassLoader(),
                new Class<?>[]{SourceDataLine.class}, (proxy, method, args) -> {
                    if (method.getName().equals("write")) {
                        pcm.write((byte[]) args[0], (Integer) args[1], (Integer) args[2]);
                        return args[2];
                    }
                    return null;
                });
    }
    private static Instrument impulse() {
        return new Instrument() {
            public double sample(long index) { return 1; }
            public long lengthSamples(long pulseSamples) { return 1; }
        };
    }
    private List<Integer> onsets(byte[] bytes) {
        var result = new ArrayList<Integer>();
        for (int i = 0; i < bytes.length; i += 2) if (bytes[i] != 0 || bytes[i + 1] != 0) result.add(i / 2);
        return result;
    }
    @Test
    public void configuredHumanizeLimitControlsJitterAndZeroDisablesOnlyTiming() throws Exception {
        var rhythm = new Rhythm("Limit", List.of(new BeepTrack("xxxxxxxxxxxxxxxx", impulse())),
                120, new TimeSignature(4, 4), 4);
        for (int limitMs : List.of(0, 10)) {
            var pcm = new ByteArrayOutputStream();
            new BeepSequencer(format -> sink(pcm), limitMs).playRhythm(rhythm, 1, 100);
            var hits = onsets(pcm.toByteArray());
            assertEquals(16, hits.size());
            assertEquals(88200, pcm.size() / 2);
            for (int i = 0; i < hits.size(); i++) {
                int straight = (int) (i * 5512.5);
                assertTrue(Math.abs(hits.get(i) - straight) <= limitMs * 44.1 + 1);
                if (limitMs == 0) assertEquals(straight, hits.get(i).intValue());
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new BeepSequencer(-1));
        assertThrows(IllegalArgumentException.class, () -> new BeepSequencer(1001));
    }

    @Test
    public void offsetsMoveOnlySelectedPulsesAndKeepTempo() throws Exception {
        var pcm = new ByteArrayOutputStream();
        var rhythm = new Rhythm("Dialect", List.of(new BeepTrack("xxxxxxxxxxxxxxxx", impulse())),
                120, new TimeSignature(4, 4), 4, Dialect.of(4, List.of(0, -0.4, 0, 0)));
        new BeepSequencer(format -> sink(pcm)).playRhythm(rhythm, 2, 0);
        assertEquals(176400, pcm.size() / 2);
        var hits = onsets(pcm.toByteArray());
        assertEquals(32, hits.size());
        for (int beat = 0; beat < 8; beat++) {
            int start = beat * 22050;
            assertEquals(List.of(start, start + 4410, start + 11025, start + 16537), hits.subList(beat * 4, beat * 4 + 4));
        }
    }
    @Test
    public void endpointPositionsCanCoincideWithoutChangingOtherPulses() throws Exception {
        var pcm = new ByteArrayOutputStream();
        var rhythm = new Rhythm("Endpoints", List.of(new BeepTrack("xxxx", impulse())), 120,
                new TimeSignature(1, 4), 4, Dialect.of(4, List.of(1, -1, 0, 0)));
        new BeepSequencer(format -> sink(pcm)).playRhythm(rhythm, 1, 100);
        assertEquals(List.of(2756, 11025, 16537), onsets(pcm.toByteArray()));
        assertEquals(22050, pcm.size() / 2);
    }

    @Test
    public void firstPulseMayMoveAcrossBeatAndLoopBoundaries() throws Exception {
        var pcm = new ByteArrayOutputStream();
        var rhythm = new Rhythm("Early", List.of(new BeepTrack("x...............", impulse())),
                120, new TimeSignature(4, 4), 4, Dialect.of(4, List.of(-1, 0, 0, 0)));
        new BeepSequencer(format -> sink(pcm)).playRhythm(rhythm, 3, 0);
        assertEquals(List.of(0, 85444, 173644), onsets(pcm.toByteArray()));
        assertEquals(264600, pcm.size() / 2);
    }
    @Test
    public void liveOffsetsTakeEffectOnNextBeatAndSurviveTempoAndHumanizeControls() throws Exception {
        var pcm = new ByteArrayOutputStream();
        var controls = new PlaybackSettings(120, 0, Dialect.straight(4));
        var sequencer = new BeepSequencer(format -> sink(pcm));
        int[] count = {0};
        Instrument instrument = new Instrument() {
            public double sample(long index) { return 1; }
            public long lengthSamples(long samples) {
                count[0]++;
                if (count[0] == 1) controls.setDialect(Dialect.of(4, List.of(0, -0.4, 0, 0)));
                if (count[0] == 40) sequencer.stop();
                return 1;
            }
        };
        sequencer.playContinuously(new Rhythm("Live", List.of(new BeepTrack("xxxxxxxxxxxxxxxx", instrument)),
                120, new TimeSignature(4, 4), 4), controls);
        var hits = onsets(pcm.toByteArray());
        assertEquals(List.of(0, 5512, 11025, 16537), hits.subList(0, 4));
        assertEquals(List.of(22050, 26460, 33075, 38587), hits.subList(4, 8));
        controls.setBpm(130); controls.setHumanize(60);
        assertEquals(Dialect.of(4, List.of(0, -0.4, 0, 0)), controls.snapshot().dialect());
    }
    private Rhythm load(String dialect) {
        String yaml = "Test:\n  bpm: 120\n  stepsPerBeat: 4\n  dialect: " + dialect
                + "\n  tracks:\n    dundun: x...............\n";
        return RhythmLoader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).get("Test");
    }
    @Test
    public void validatesIndependentOffsetsWithoutSumOrGapConstraints() {
        assertEquals(Dialect.straight(4), load("[0, 0, 0, 0]").dialect());
        assertEquals(0.0, load("[1, -1, 1, -1]").dialect().minimumGap(), 0);
        assertEquals(List.of(1.0, 1.0, 1.0, 1.0), load("[1, 1, 1, 1]").dialect().offsets());
        assertThrows(UnsupportedOperationException.class, () -> load("[0, 0, 0, 0]").dialect().offsets().clear());
        for (String invalid : List.of("[]", "null", "[0, 0, 0]", "[1.1, 0, 0, 0]", "[-1.1, 0, 0, 0]",
                "[.nan, 0, 0, 0]", "[.inf, 0, 0, 0]", "['0', 0, 0, 0]")) {
            assertThrows(IllegalArgumentException.class, () -> load(invalid));
        }
    }
}
