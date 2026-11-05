package picosoft.javafro.sequencer;

import org.junit.Test;
import picosoft.javafro.base.Instrument;
import picosoft.javafro.base.Rhythm;
import javax.sound.sampled.SourceDataLine;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class MixerPlaybackTest {
    @Test
    public void mixerChangesSoundingVoicesIndependentlyAndMutePreservesVolume() throws Exception {
        var settings = new PlaybackSettings(120, 0);
        var amplitudes = new ArrayList<Integer>();
        BeepSequencer[] sequencer = new BeepSequencer[1];
        SourceDataLine line = (SourceDataLine) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SourceDataLine.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("write")) return null;
                    byte[] bytes = (byte[]) args[0];
                    amplitudes.add((int) (short) ((bytes[0] & 255) | bytes[1] << 8));
                    switch (amplitudes.size()) {
                        case 1 -> settings.setTrackMix(0, 25, true);
                        case 2 -> settings.setTrackMix(1, 50, false);
                        case 3 -> settings.setTrackMix(0, 25, false);
                        case 4 -> settings.setMasterVolume(100);
                        case 5 -> settings.setMasterVolume(0);
                        case 6 -> sequencer[0].stop();
                    }
                    return args[2];
                });
        sequencer[0] = new BeepSequencer(format -> line);
        Instrument sustained = new Instrument() {
            public double sample(long index) { return 1; }
            public long lengthSamples(long pulse) { return 44100; }
        };
        sequencer[0].playContinuously(new Rhythm("Mix", List.of(
                new BeepTrack("x.......", sustained), new BeepTrack("x.......", sustained)), 120), settings);
        assertEquals(6, amplitudes.size());
        assertEquals(amplitudes.get(0) / 2.0, amplitudes.get(1), 1);
        assertEquals(amplitudes.get(0) / 4.0, amplitudes.get(2), 1);
        assertEquals(amplitudes.get(0) * .375, amplitudes.get(3), 1);
        assertEquals(amplitudes.get(3) * 4.0, amplitudes.get(4), 4);
        assertEquals(0, (int) amplitudes.get(5));
        assertEquals(new PlaybackSettings.TrackMix(25, false), settings.trackMix(0));
        settings.setTrackMix(0, 200, false);
        assertEquals(2, settings.trackMix(0).gain(), 0);
        assertThrows(IllegalArgumentException.class, () -> settings.setTrackMix(0, 201, false));
        assertThrows(IllegalArgumentException.class, () -> settings.setMasterVolume(-1));
        assertThrows(IllegalArgumentException.class, () -> settings.setMasterVolume(101));
    }
}
