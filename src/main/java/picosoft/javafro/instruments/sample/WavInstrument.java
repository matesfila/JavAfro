package picosoft.javafro.instruments.sample;

import picosoft.javafro.base.AudioConstants;
import picosoft.javafro.base.Instrument;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.net.URL;

/**
 * Nástroj, ktorý pri každom údere prehrá načítaný audio sample (napríklad WAV).
 *
 * <p>Súbor sa pri vytvorení jednorazovo načíta a cez Java Sound prevedie na formát
 * výstupu ({@link AudioConstants#audioFormat()}): 44 100 Hz, 16 bitov, mono. Vzorky sa uložia
 * ako {@code double[]} v intervale [-1, 1]. {@link #sample(long)} vracia vzorku podľa
 * času od spustenia úderu a po konci samplu ticho.</p>
 *
 * <p>Sample znie celú svoju dĺžku bez obálky, aj ak je dlhší ako jeden pulz; sekvencer
 * ho nechá doznieť a zmieša ho s ďalšími údermi.</p>
 */
public final class WavInstrument implements Instrument {
    private final double[] samples;

    public WavInstrument(Path file) throws IOException, UnsupportedAudioFileException {
        this(file.toFile());
    }

    public WavInstrument(File file) throws IOException, UnsupportedAudioFileException {
        this(file.toURI().toURL());
    }

    /** Supports bundled resources, including jar:file URLs. */
    public WavInstrument(URL resource) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(resource);
             AudioInputStream pcm = toOutputFormat(source)) {
            this.samples = decode(pcm.readAllBytes());
        }
        if (samples.length == 0) {
            throw new IllegalArgumentException("Audio file contains no samples: " + resource);
        }
    }

    private static AudioInputStream toOutputFormat(AudioInputStream source) {
        AudioFormat target = AudioConstants.audioFormat();
        AudioFormat sourceFormat = source.getFormat();
        AudioInputStream input = source;
        boolean isPcm = sourceFormat.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
                || sourceFormat.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED
                || sourceFormat.getEncoding() == AudioFormat.Encoding.PCM_FLOAT;
        if (!isPcm) {
            // Kódované formáty (napr. u-law) najprv dekódujeme na PCM so zachovaným vzorkovaním.
            AudioFormat decoded = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(), 16,
                    sourceFormat.getChannels(), sourceFormat.getChannels() * 2, sourceFormat.getSampleRate(), false);
            input = AudioSystem.getAudioInputStream(decoded, input);
        }
        if (!AudioSystem.isConversionSupported(target, input.getFormat())) {
            throw new IllegalArgumentException("Unsupported audio format: " + sourceFormat);
        }
        return AudioSystem.getAudioInputStream(target, input);
    }

    private static double[] decode(byte[] bytes) {
        double[] result = new double[bytes.length / 2];
        for (int i = 0; i < result.length; i++) {
            short value = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
            result[i] = value / 32768.0;
        }
        return result;
    }

    @Override
    public double sample(long sampleIndex) {
        return sampleIndex >= 0 && sampleIndex < samples.length ? samples[(int) sampleIndex] : 0.0;
    }

    @Override
    public long lengthSamples(long pulseSamples) {
        return samples.length;
    }
}
