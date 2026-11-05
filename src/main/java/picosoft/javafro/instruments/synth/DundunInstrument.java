package picosoft.javafro.instruments.synth;

import picosoft.javafro.base.BeepInstrument;
import picosoft.javafro.instruments.DrumInstrument;
import picosoft.javafro.instruments.sample.matesamples.DundunBellInstrument;

public final class DundunInstrument extends DrumInstrument {
    private static final int FREQUENCY_HZ = 110;
    public DundunInstrument() {
        super(new BeepInstrument(FREQUENCY_HZ), new DundunBellInstrument(), 'D');
    }
}
