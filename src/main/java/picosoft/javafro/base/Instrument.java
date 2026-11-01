package picosoft.javafro.base;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Zdroj zvuku, ktorý sekvencer spúšťa pri každom údere ({@code x}).
 * Čas vo vzorkách sa vždy počíta od začiatku konkrétneho úderu pri 44 100 Hz.
 */
public interface Instrument {
    /** Notes this instrument understands; a dot is handled as silence by the sequencer. */
    default boolean supportsNote(char note) { return note == 'x'; }

    /** Select one immutable voice per hit, so multisamples never change during a sounding note. */
    default Instrument voice(char note, int humanize) {
        if (!supportsNote(note)) throw new IllegalArgumentException("Unsupported note: " + note);
        return this;
    }

    /** Vráti normalizovanú vzorku v intervale [-1, 1] {@code sampleIndex} vzoriek po začiatku úderu. */
    double sample(long sampleIndex);

    /**
     * Počet vzoriek, počas ktorých úder znie. Môže byť dlhší ako pulz; sekvencer ho nechá doznieť
     * a prelíše s nasledujúcimi úderami.
     *
     * @param pulseSamples dĺžka jedného pulzu (šestnástiny) vo vzorkách
     */
    default long lengthSamples(long pulseSamples) {
        return pulseSamples;
    }

    /** Multiplikátor amplitúdy v čase úderu; predvolene bez obálky. */
    default double envelope(long sampleIndex, long lengthSamples) {
        return 1.0;
    }

    /** Prehrá nástroj po zadaný čas v milisekundách; po konci nástroja znie ticho. */
    default void playTone(int durationMs) throws LineUnavailableException {
        if (durationMs <= 0) {
            throw new IllegalArgumentException("Tone duration must be greater than zero milliseconds.");
        }
        AudioFormat format = AudioConstants.audioFormat();
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();

            long totalSamples = (long) AudioConstants.SAMPLE_RATE * durationMs / 1000;
            byte[] buffer = new byte[AudioConstants.SAMPLES_PER_BUFFER * 2];
            long sampleIndex = 0;
            while (sampleIndex < totalSamples) {
                int samplesToWrite = (int) Math.min(AudioConstants.SAMPLES_PER_BUFFER, totalSamples - sampleIndex);
                for (int i = 0; i < samplesToWrite; i++, sampleIndex++) {
                    AudioConstants.putSample(buffer, i, sample(sampleIndex));
                }
                line.write(buffer, 0, samplesToWrite * 2);
            }
            line.drain();
        }
    }
}
