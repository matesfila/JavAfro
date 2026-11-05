package picosoft.javafro.instruments.synth;

import picosoft.javafro.base.BeepInstrument;
import picosoft.javafro.instruments.DrumInstrument;
import picosoft.javafro.instruments.sample.matesamples.KensereniBellInstrument;

public final class KensereniInstrument extends DrumInstrument {
    private static final int FREQUENCY_HZ = 370;
    public KensereniInstrument() {
        super(new BeepInstrument(FREQUENCY_HZ), new KensereniBellInstrument(), 'K');
    }
}
