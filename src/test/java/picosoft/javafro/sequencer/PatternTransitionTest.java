package picosoft.javafro.sequencer;

import org.junit.Test;
import picosoft.javafro.base.*;

import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class PatternTransitionTest {
    private static class Sink {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int opens, starts, drains;
        Runnable afterWrite = () -> {};
        final SourceDataLine line = (SourceDataLine) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SourceDataLine.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "open" -> opens++;
                        case "start" -> starts++;
                        case "drain" -> drains++;
                        case "write" -> {
                            bytes.write((byte[]) args[0], (int) args[1], (int) args[2]);
                            afterWrite.run();
                            return args[2];
                        }
                    }
                    return null;
                });
        int sample(int frame) {
            byte[] pcm = bytes.toByteArray();
            return (short) ((pcm[2 * frame] & 255) | pcm[2 * frame + 1] << 8);
        }
    }

    private static Instrument impulse(double amplitude, AtomicInteger hits) {
        return new Instrument() {
            public double sample(long index) { return amplitude; }
            public long lengthSamples(long pulse) { hits.incrementAndGet(); return 1; }
        };
    }

    @Test
    public void switchesAtFullPatternEndOnOneLineAndPreservesOldTailMix() throws Exception {
        var sink = new Sink();
        var sequencer = new BeepSequencer(format -> sink.line);
        int boundary = 176400; // Two 4/4 bars at 120 BPM, regardless of the next rhythm's tempo.
        var oldSettings = new PlaybackSettings(120, 0, Dialect.straight(4));
        oldSettings.setMasterVolume(100);
        oldSettings.setTrackMix(0, 50, false);
        var nextSettings = new PlaybackSettings(240, 0, Dialect.straight(3));
        nextSettings.setMasterVolume(100);
        var nextHits = new AtomicInteger();
        var skippedHits = new AtomicInteger();
        var callbacks = new AtomicInteger();
        var next = new Rhythm("Next", List.of(
                new BeepTrack("xxxxxxxxxxxx", impulse(.5, nextHits)),
                new BeepTrack("............", impulse(1, new AtomicInteger()))),
                240, new TimeSignature(12, 8), 3);
        var skipped = new Rhythm("Skipped", List.of(new BeepTrack("xxxxxxxx", impulse(1, skippedHits))), 120);
        Instrument sustained = new Instrument() {
            public double sample(long index) { return .2; }
            public long lengthSamples(long pulse) {
                assertTrue(sequencer.queueRhythm(skipped, new PlaybackSettings(120, 0), () -> fail("Superseded rhythm started")));
                assertTrue(sequencer.queueRhythm(next, nextSettings, () -> {
                    assertTrue(sink.bytes.size() / 2 >= boundary);
                    callbacks.incrementAndGet();
                }));
                return boundary + 2048;
            }
        };
        sink.afterWrite = () -> { if (sink.bytes.size() / 2 > boundary + 5000) sequencer.stop(); };
        sequencer.playContinuously(new Rhythm("Old", List.of(
                new BeepTrack("x...............|................", sustained)),
                120, new TimeSignature(4, 4), 4), oldSettings);

        assertEquals(1, sink.opens);
        assertEquals(1, sink.starts);
        assertEquals(0, sink.drains);
        assertEquals(0, skippedHits.get());
        assertTrue(nextHits.get() > 0);
        assertEquals(1, callbacks.get());
        assertEquals(.1 * Short.MAX_VALUE, sink.sample(boundary - 1), 1);
        assertEquals(.35 * Short.MAX_VALUE, sink.sample(boundary), 1); // Old tail + new two-track mix.
        assertEquals(.1 * Short.MAX_VALUE, sink.sample(boundary + 1), 1);
        assertEquals(0, sink.sample(boundary + 2048));
        assertEquals(.25 * Short.MAX_VALUE, sink.sample(boundary + 3675), 1); // New 240 BPM / 3-step grid.
    }

    @Test
    public void stopDiscardsQueuedRhythmAndRejectsFurtherQueueRequests() throws Exception {
        var sink = new Sink();
        var sequencer = new BeepSequencer(format -> sink.line);
        var nextHits = new AtomicInteger();
        var next = new Rhythm("Next", List.of(new BeepTrack("xxxxxxxx", impulse(1, nextHits))), 120);
        Instrument old = new Instrument() {
            public double sample(long index) { return 1; }
            public long lengthSamples(long pulse) {
                assertTrue(sequencer.queueRhythm(next, new PlaybackSettings(120, 0), () -> fail("Stopped queue started")));
                sequencer.stop();
                return 1;
            }
        };
        sequencer.playContinuously(new Rhythm("Old", List.of(new BeepTrack("xxxxxxxx", old)), 120), new PlaybackSettings(120, 0));
        assertEquals(0, nextHits.get());
        assertFalse(sequencer.queueRhythm(next, new PlaybackSettings(120, 0), () -> fail("Stopped sequencer restarted")));
        assertEquals(1, sink.opens);
    }
}
