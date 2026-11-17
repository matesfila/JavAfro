package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer sangban multisamples without a bell. */
public final class SangbanDrumInstrument extends AfroSampleInstrument {
    public SangbanDrumInstrument() { this(ThreadLocalRandom.current()); }
    public SangbanDrumInstrument(RandomGenerator random) { super("sangban", random); }
}
