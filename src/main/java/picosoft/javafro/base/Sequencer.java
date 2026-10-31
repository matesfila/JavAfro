package picosoft.javafro.base;

import javax.sound.sampled.LineUnavailableException;
import java.util.List;

public interface Sequencer {
    /** Plays the rhythm once with exact timing. */
    default void playPattern(Rhythm rhythm) throws LineUnavailableException {
        playPattern(rhythm, 1, 0);
    }

    default void playPattern(Rhythm rhythm, int repetitions) throws LineUnavailableException {
        playPattern(rhythm, repetitions, 0);
    }

    void playPattern(Rhythm rhythm, int repetitions, int humanize) throws LineUnavailableException;

    void playPattern(String pattern, int repetitions, int bpm) throws LineUnavailableException;

    /** Humanize ranges from 0 (exact timing) to 100 (maximum variation). */
    void playPattern(String pattern, int repetitions, int bpm, int humanize) throws LineUnavailableException;

    /** Each character is a sixteenth note; shorter patterns loop within the longest pattern. */
    void playPattern(List<? extends Track> tracks, int repetitions, int bpm) throws LineUnavailableException;

    /** Humanize ranges from 0 (exact timing) to 100 (maximum variation). */
    void playPattern(List<? extends Track> tracks, int repetitions, int bpm, int humanize) throws LineUnavailableException;
}
