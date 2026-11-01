package picosoft.javafro.instruments.synth;

import picosoft.javafro.base.BeepInstrument;

public final class KensereniInstrument extends BeepInstrument {
    private static final int FREQUENCY_HZ = 370;
    public KensereniInstrument() {
        super(FREQUENCY_HZ);
    }
}
