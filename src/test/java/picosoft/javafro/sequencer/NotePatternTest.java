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

public class NotePatternTest {
    @Test public void tokenizesAndValidatesBars() {
        assertEquals(List.of("B", ".", "T", "."), NotePattern.notes("B.T."));
        assertEquals(List.of("g0", ".", "h0", "c1"), NotePattern.notes("  g0   .| h0 c1  "));
        assertEquals(List.of("BT", "."), NotePattern.notes("BT ."));
        for (String invalid : List.of("", "  ", "B . | ", "|B .", "B . || T .", "B ?")) {
            assertFalse(Track.isValidPattern(invalid));
        }
        var meter = new TimeSignature(2, 4);
        assertEquals(List.of("g0", ".", "g0", ".", "c1", ".", "c1", "."),
                NotePattern.notes(meter.expandPattern("g0 .|c1 .", 2)));
        assertThrows(IllegalArgumentException.class, () -> meter.expandPattern("g0 . c1", 2));
    }

    @Test public void loadsSpacedDrumsAndVariationsButRejectsUnknownWholeNotes() {
        String yaml = "Test:\n  tracks:\n    djembe:\n      base: ' B . T . '\n      variations: ['S . . .']";
        var rhythm = RhythmLoader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).get("Test");
        assertEquals(8, NotePattern.notes(rhythm.timeSignature().expandPattern(rhythm.tracks().getFirst().pattern(), 2)).size());
        assertEquals(List.of("S", ".", ".", "."), NotePattern.notes(rhythm.tracks().getFirst().variations().getFirst()));
        assertThrows(IllegalArgumentException.class, () -> RhythmLoader.load(new ByteArrayInputStream(
                yaml.replace("B . T .", "BT . . .").getBytes(StandardCharsets.UTF_8))));
    }

    @Test public void playsWholeTokensOnConsecutivePulsesAndKeepsBarBoundaries() throws Exception {
        var received = new ArrayList<String>();
        Instrument instrument = new Instrument() {
            public boolean supportsNote(String note) { return List.of("g0", "c1").contains(note); }
            public Instrument voice(String note, int humanize) { received.add(note); return this; }
            public double sample(long index) { return 1; }
            public long lengthSamples(long pulse) { return 1; }
        };
        var pcm = new ByteArrayOutputStream();
        SourceDataLine sink = (SourceDataLine) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SourceDataLine.class}, (proxy, method, args) -> {
                    if (method.getName().equals("write")) {
                        pcm.write((byte[]) args[0], (Integer) args[1], (Integer) args[2]);
                        return args[2];
                    }
                    return null;
                });
        var rhythm = new Rhythm("Tokens", List.of(new BeepTrack(" g0   .|c1 . ", instrument)),
                120, new TimeSignature(1, 4));
        new BeepSequencer(format -> sink).playRhythm(rhythm, 2, 0);
        assertEquals(List.of("g0", "c1", "g0", "c1"), received);
        assertEquals(88200, pcm.size() / 2);
        var onsets = new ArrayList<Integer>();
        byte[] bytes = pcm.toByteArray();
        for (int i = 0; i < bytes.length; i += 2) if (bytes[i] != 0 || bytes[i + 1] != 0) onsets.add(i / 2);
        assertEquals(List.of(0, 22050, 44100, 66150), onsets);
    }
}
