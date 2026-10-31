package picosoft.javafro.base;

import javax.sound.sampled.LineUnavailableException;
import java.util.List;

public interface Sequencer {
    void playPattern(String pattern, int repetitions, int bpm) throws LineUnavailableException;

    /** Each character is a sixteenth note; shorter patterns loop within the longest pattern. */
    void playPattern(List<? extends Track> tracks, int repetitions, int bpm) throws LineUnavailableException;
}
