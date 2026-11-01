package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer dundun_bell multisamples and note mapping. */
public final class DundunBellInstrument extends AfroSampleInstrument {
    public DundunBellInstrument() { this(ThreadLocalRandom.current()); }
    public DundunBellInstrument(RandomGenerator random) { super("dundun_bell", random); }
}
