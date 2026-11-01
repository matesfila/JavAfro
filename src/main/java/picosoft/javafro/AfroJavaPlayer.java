package picosoft.javafro;

import picosoft.javafro.base.Rhythm;
import picosoft.javafro.io.RhythmLoader;
import picosoft.javafro.sequencer.BeepSequencer;

import java.io.IOException;
import javax.sound.sampled.LineUnavailableException;

public class AfroJavaPlayer {

    public static void main(String[] args) throws LineUnavailableException, IOException {

        playRhythm("Kon");

    }

    private static void playRhythm(String rhythmName) throws IOException, LineUnavailableException {
        try (var input = AfroJavaPlayer.class.getResourceAsStream("/rhythms/imported/%s.yaml".formatted(rhythmName))) {
            Rhythm rhythm = RhythmLoader.load(input).get(rhythmName);
            new BeepSequencer().playPattern(rhythm, 32, 40);
        }
    }

}
