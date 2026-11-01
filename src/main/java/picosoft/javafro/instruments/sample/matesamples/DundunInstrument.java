package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer dundun multisamples and note mapping. */
public final class DundunInstrument extends AfroSampleInstrument {
    public DundunInstrument() { this(ThreadLocalRandom.current()); }
    public DundunInstrument(RandomGenerator random) { super("dundun", random); }
}
