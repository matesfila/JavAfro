package picosoft.javafro.instruments.sample;

import picosoft.javafro.base.Instrument;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Immutable decoded audio is shared; each sequencer voice has its own playback position. */
public abstract class WavSampleInstrument implements Instrument {
    private static final ConcurrentHashMap<String, WavInstrument> CACHE = new ConcurrentHashMap<>();
    private final WavInstrument audio;

    public WavSampleInstrument(String resource) {
        audio = loadSample(resource);
    }

    public static WavInstrument loadSample(String resource) {
        return CACHE.computeIfAbsent(resource, path -> {
            var url = WavSampleInstrument.class.getResource(path);
            if (url == null) throw new IllegalArgumentException("Missing wav sample: " + path);
            try {
                return new WavInstrument(url);
            } catch (IOException | UnsupportedAudioFileException exception) {
                throw new IllegalArgumentException("Cannot load wav sample: " + path, exception);
            }
        });
    }

    @Override
    public final double sample(long sampleIndex) {
        return audio.sample(sampleIndex);
    }

    @Override
    public final long lengthSamples(long pulseSamples) {
        return audio.lengthSamples(pulseSamples);
    }
}
