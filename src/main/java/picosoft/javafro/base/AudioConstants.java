package picosoft.javafro.base;

import javax.sound.sampled.AudioFormat;

/**
 * Spoločné parametre zvukového výstupu: 44 100 Hz, 16 bitov, mono, signed PCM, little-endian.
 * Používajú ich nástroje aj sekvencer, takže nezávisia od konkrétnej implementácie nástroja.
 */
public final class AudioConstants {
    public static final int SAMPLE_RATE = 44100;
    public static final int SAMPLES_PER_BUFFER = 4096;
    private static final double OUTPUT_GAIN = 0.25;

    private AudioConstants() {
    }

    public static AudioFormat audioFormat() {
        return new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    }

    /** Zapíše amplitúdu z intervalu [-1, 1] ako 16-bitovú vzorku na pozíciu {@code index} v bufferi. */
    public static void putSample(byte[] buffer, int index, double amplitude) {
        double clamped = Math.max(-1.0, Math.min(1.0, amplitude));
        short sample = (short) (clamped * Short.MAX_VALUE * OUTPUT_GAIN);
        buffer[index * 2] = (byte) sample;
        buffer[index * 2 + 1] = (byte) (sample >> 8);
    }
}
