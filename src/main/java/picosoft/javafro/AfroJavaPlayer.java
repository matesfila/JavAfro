package picosoft.javafro;

import picosoft.javafro.instruments.DundunInstrument;
import picosoft.javafro.instruments.KensereniInstrument;
import picosoft.javafro.instruments.SangbanInstrument;
import picosoft.javafro.sequencer.BeepSequencer;
import picosoft.javafro.sequencer.BeepTrack;

import javax.sound.sampled.LineUnavailableException;
import java.util.List;

public class AfroJavaPlayer {

    public static void main(String[] args) throws LineUnavailableException {
        new BeepSequencer().playPattern(List.of(
                new BeepTrack("x...x...x.x.x...", new DundunInstrument()),
                new BeepTrack("..x...xx", new SangbanInstrument()),
                new BeepTrack("x.xx.xx.", new KensereniInstrument())
        ), 32, 116);
    }

}
