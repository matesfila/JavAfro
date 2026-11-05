package picosoft.javafro.instruments.synth;

import picosoft.javafro.base.BeepInstrument;
import picosoft.javafro.instruments.DrumInstrument;
import picosoft.javafro.instruments.sample.matesamples.SangbanBellInstrument;

public final class SangbanInstrument extends DrumInstrument {
    private static final int FREQUENCY_HZ = 220;
    public SangbanInstrument() {
        super(new BeepInstrument(FREQUENCY_HZ), new SangbanBellInstrument(), 'S');
    }
}
