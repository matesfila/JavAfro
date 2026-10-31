package picosoft.javafro;

import picosoft.javafro.base.Rhythm;
import picosoft.javafro.io.RhythmLoader;
import picosoft.javafro.sequencer.BeepSequencer;

import java.io.IOException;
import javax.sound.sampled.LineUnavailableException;

public class AfroJavaPlayer {

    public static void main(String[] args) throws LineUnavailableException, IOException {
        try (var input = AfroJavaPlayer.class.getResourceAsStream("/rhythms/djole.yaml")) {
            Rhythm rhythm = RhythmLoader.load(input).get("Djole");
            new BeepSequencer().playPattern(rhythm, 32, 40);
        }

//        new BeepSequencer().playPattern(List.of(
//                new BeepTrack("x.x.x.x.", new DundunInstrument()),
//                new BeepTrack("x.x.x.x.", new SangbanInstrument()),
//                new BeepTrack("x.x.x.x.", new KensereniInstrument())
//        ), 32, 116, 80);
    }

}
