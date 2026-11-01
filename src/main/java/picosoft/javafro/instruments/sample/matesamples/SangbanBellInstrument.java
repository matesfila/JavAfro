package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer sangban_bell multisamples and note mapping. */
public final class SangbanBellInstrument extends AfroSampleInstrument {
    public SangbanBellInstrument() { this(ThreadLocalRandom.current()); }
    public SangbanBellInstrument(RandomGenerator random) { super("sangban_bell", random); }
}
