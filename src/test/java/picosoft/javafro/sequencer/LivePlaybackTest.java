package picosoft.javafro.sequencer;

import org.junit.Test;
import picosoft.javafro.base.Instrument;
import picosoft.javafro.base.Rhythm;
import picosoft.javafro.base.TimeSignature;
import picosoft.javafro.io.RhythmLoader;

import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class LivePlaybackTest {
    private static class AudioSink {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final CountDownLatch writing = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        boolean block;
        int drains;
        final SourceDataLine line = (SourceDataLine) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SourceDataLine.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "write" -> {
                            writing.countDown();
                            if (block && !closed.await(3, TimeUnit.SECONDS)) throw new AssertionError("Write did not unblock");
                            if (closed.getCount() == 0) yield 0;
                            bytes.write((byte[]) args[0], (Integer) args[1], (Integer) args[2]);
                            yield args[2];
                        }
                        case "close" -> { closed.countDown(); yield null; }
                        case "drain" -> { drains++; yield null; }
                        case "isOpen", "isRunning", "isActive" -> closed.getCount() > 0;
                        default -> null;
                    };
                });
    }

    private static Instrument impulse() {
        return new Instrument() {
            public double sample(long index) { return 1; }
            public long lengthSamples(long pulseSamples) { return 1; }
        };
    }

    private int renderedFrames(Rhythm definition, int bpm) throws Exception {
        var sink = new AudioSink();
        var tracks = definition.tracks().stream()
                // Timing-only impulse supports x; preserve the positions of all instrument notes.
                .map(track -> new BeepTrack(track.pattern().replaceAll("[a-zA-Z]", "x"), impulse())).toList();
        var rhythm = new Rhythm(definition.name(), tracks, bpm, definition.timeSignature(), definition.stepsPerBeat(), definition.dialect());
        new BeepSequencer(format -> sink.line).playPattern(rhythm, 1, 0);
        return sink.bytes.size() / 2;
    }

    @Test
    public void halfBarsRepeatLocallyAndKeepFullBarDuration() throws Exception {
        for (String shortPattern : List.of("x.......", "x.......|....x...")) {
            String fullPattern = java.util.Arrays.stream(shortPattern.split("\\|"))
                    .map(bar -> bar.repeat(2)).collect(java.util.stream.Collectors.joining("|"));
            var shortSink = new AudioSink();
            var fullSink = new AudioSink();
            var shortRhythm = new Rhythm("Short", List.of(new BeepTrack(shortPattern, impulse())),
                    120, new TimeSignature(4, 4), 4);
            var fullRhythm = new Rhythm("Full", List.of(new BeepTrack(fullPattern, impulse())),
                    120, new TimeSignature(4, 4), 4);
            new BeepSequencer(format -> shortSink.line).playPattern(shortRhythm, 1, 0);
            new BeepSequencer(format -> fullSink.line).playPattern(fullRhythm, 1, 0);
            assertArrayEquals(fullSink.bytes.toByteArray(), shortSink.bytes.toByteArray());
            assertEquals(88200 * shortPattern.split("\\|").length, shortSink.bytes.size() / 2);
        }
    }

    @Test
    public void djoleCycleIsFourBeatsAndMatchesImportedTiming() throws Exception {
        var djole = RhythmLoader.load(Path.of("rhythms/djole.yaml")).get("djole");
        var original = RhythmLoader.load(Path.of("rhythms/imported/Djole.yaml")).get("Djole");
        assertEquals(88200, renderedFrames(djole, 120)); // Four beats at 120 BPM = two seconds.
        assertEquals(renderedFrames(original, 120), renderedFrames(djole, 120));
        assertEquals(44100, renderedFrames(djole, 240));
        assertEquals(44100 * 240.0 / 116, renderedFrames(djole, 116), 1.0);
    }

    @Test
    public void meterAndSubdivisionDetermineDurationWhilePipesOnlyMarkBars() throws Exception {
        var sixteenths = new Rhythm("4/4", List.of(new BeepTrack("x...............", impulse())),
                120, new TimeSignature(4, 4), 4);
        var split = new Rhythm("2/4", List.of(new BeepTrack("x.......|........", impulse())),
                120, new TimeSignature(2, 4), 4);
        var compound = new Rhythm("12/8", List.of(new BeepTrack("x..x..x..x..", impulse())),
                120, new TimeSignature(12, 8));
        var eightFour = new Rhythm("8/4", List.of(new BeepTrack("x...............", impulse())),
                120, new TimeSignature(8, 4));
        assertEquals(88200, renderedFrames(sixteenths, 120));
        assertEquals(88200, renderedFrames(split, 120));
        assertEquals(88200, renderedFrames(compound, 120));
        assertEquals(176400, renderedFrames(eightFour, 120));
    }

    @Test
    public void finitePlaybackKeepsExactTempoAndIgnoresBarSeparators() throws Exception {
        var sink = new AudioSink();
        var sequencer = new BeepSequencer(format -> sink.line);
        sequencer.playPattern(List.of(new BeepTrack("x...|x...", impulse())), 2, 120, 0);
        byte[] pcm = sink.bytes.toByteArray();
        assertEquals(88200, pcm.length / 2);
        var onsets = new ArrayList<Integer>();
        for (int i = 0; i < pcm.length; i += 2) if (pcm[i] != 0 || pcm[i + 1] != 0) onsets.add(i / 2);
        assertEquals(List.of(0, 22050, 44100, 66150), onsets);
        assertEquals(1, sink.drains);
    }

    @Test
    public void continuousPlaybackAppliesLiveTempoAndHumanizeAcrossLoops() throws Exception {
        var sink = new AudioSink();
        var controls = new PlaybackSettings(120, 100);
        var sequencer = new BeepSequencer(format -> sink.line);
        List<Long> durations = new ArrayList<>();
        Instrument instrument = new Instrument() {
            public double sample(long index) { return 1; }
            public long lengthSamples(long pulseSamples) {
                durations.add(pulseSamples);
                if (durations.size() == 4) {
                    controls.setBpm(240);
                    controls.setHumanize(0);
                }
                if (durations.size() == 21) sequencer.stop();
                return 1;
            }
        };
        var rhythm = new Rhythm("Live", List.of(new BeepTrack("xxxxxxxx", instrument)), 120);
        sequencer.playContinuously(rhythm, controls);
        assertEquals(21, durations.size()); // More than two complete loops, no repetition limit.
        assertEquals(List.of(11025L, 11025L, 11025L, 11025L), durations.subList(0, 4));
        assertEquals(List.of(5512L, 5513L, 5512L, 5513L), durations.subList(4, 8));
        byte[] pcm = sink.bytes.toByteArray();
        long grid = 0;
        int checked = 0;
        for (int i = 0; i < 19; i++) {
            // Stop discards the lookahead needed for early dialect positions.
            if (i >= 4 && grid * 2 + 1 < pcm.length) {
                // Humanize was disabled live: later hits must land exactly on the changing tempo grid.
                assertTrue(pcm[(int) grid * 2] != 0 || pcm[(int) grid * 2 + 1] != 0);
                checked++;
            }
            grid += durations.get(i);
        }
        assertTrue(checked >= 8);
        assertEquals(0, sink.drains);
    }

    @Test
    public void stopUnblocksAnAudioWrite() throws Exception {
        var sink = new AudioSink();
        sink.block = true;
        var sequencer = new BeepSequencer(format -> sink.line);
        var rhythm = new Rhythm("Stop", List.of(new BeepTrack("xxxxxxxx", impulse())), 120);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> {
                sequencer.playContinuously(rhythm, new PlaybackSettings(120, 0));
                return null;
            });
            assertTrue(sink.writing.await(2, TimeUnit.SECONDS));
            sequencer.stop();
            future.get(2, TimeUnit.SECONDS);
            assertTrue(sequencer.isStopped());
            assertEquals(0, sink.drains);
        }
    }

    @Test
    public void stopBeforeStartDoesNotOpenAudioAndControlsRejectInvalidValues() throws Exception {
        var opens = new AtomicInteger();
        var sequencer = new BeepSequencer(format -> { opens.incrementAndGet(); return new AudioSink().line; });
        sequencer.stop();
        sequencer.playContinuously(new Rhythm("Stopped", List.of(new BeepTrack("xxxxxxxx", impulse())), 120),
                new PlaybackSettings(120, 0));
        assertEquals(0, opens.get());
        var controls = new PlaybackSettings(120, 40);
        assertThrows(IllegalArgumentException.class, () -> controls.setBpm(0));
        assertThrows(IllegalArgumentException.class, () -> controls.setHumanize(101));
        assertEquals(new PlaybackSettings.Values(120, 40), controls.snapshot());
    }
}
