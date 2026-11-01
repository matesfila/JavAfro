package picosoft.javafro.instruments.synth;

import picosoft.javafro.base.BeepInstrument;

public final class DundunInstrument extends BeepInstrument {
    private static final int FREQUENCY_HZ = 110;
    public DundunInstrument() {
        super(FREQUENCY_HZ);
    }
}
