package picosoft.javafro.base;

import javax.sound.sampled.AudioFormat;

/**
 * Spoločné parametre zvukového výstupu: 44 100 Hz, 16 bitov, mono, signed PCM, little-endian.
 * Používajú ich nástroje aj sekvencer, takže nezávisia od konkrétnej implementácie nástroja.
 */
public final class AudioConstants {
    public static final int SAMPLE_RATE = 44100;
    public static final int SAMPLE_SIZE_BITS = 16;
    public static final int CHANNEL_COUNT = 1;
    public static final int BYTES_PER_SAMPLE = SAMPLE_SIZE_BITS / Byte.SIZE;
    public static final double PCM_NORMALIZATION_SCALE = 32768.0;
    public static final int SAMPLES_PER_BUFFER = 4096;
    public static final int OUTPUT_LINE_BUFFER_SAMPLES = 2048;
    public static final double SECONDS_PER_MINUTE = 60.0;
    public static final int MILLISECONDS_PER_SECOND = 1000;
    public static final int HUMANIZE_PERCENT_MAX = 100;
    public static final int DEFAULT_HUMANIZE_MAX_OFFSET_MS = 30;
    public static final int MAX_HUMANIZE_OFFSET_MS = 1000;
    /** Full dialect offset moves a pulse by half a straight step. */
    public static final double DIALECT_STEP_FRACTION = 0.5;
    public static final double HUMANIZE_GAP_FRACTION = 0.25;
    public static final int MIN_SAMPLES_PER_PULSE = 4;
    private static final double OUTPUT_GAIN = 0.25;

    private AudioConstants() {
    }

    public static AudioFormat audioFormat() {
        return new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNEL_COUNT, true, false);
    }

    /** Zapíše amplitúdu z intervalu [-1, 1] ako 16-bitovú vzorku na pozíciu {@code index} v bufferi. */
    public static void putSample(byte[] buffer, int index, double amplitude) {
        // Apply master headroom before clipping: original bank gains and drum+bell sums can exceed one.
        double clamped = Math.max(-1.0, Math.min(1.0, amplitude * OUTPUT_GAIN));
        short sample = (short) (clamped * Short.MAX_VALUE);
        buffer[index * BYTES_PER_SAMPLE] = (byte) sample;
        buffer[index * BYTES_PER_SAMPLE + 1] = (byte) (sample >> Byte.SIZE);
    }
}
