package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer dundun multisamples without a bell. */
public final class DundunDrumInstrument extends AfroSampleInstrument {
    public DundunDrumInstrument() { this(ThreadLocalRandom.current()); }
    public DundunDrumInstrument(RandomGenerator random) { super("dundun", random); }
}
