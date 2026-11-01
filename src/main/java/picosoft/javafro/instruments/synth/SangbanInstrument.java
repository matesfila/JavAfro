package picosoft.javafro.instruments.synth;

import picosoft.javafro.base.BeepInstrument;

public final class SangbanInstrument extends BeepInstrument {
    private static final int FREQUENCY_HZ = 220;
    public SangbanInstrument() {
        super(FREQUENCY_HZ);
    }
}
