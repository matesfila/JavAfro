package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer sangban multisamples and note mapping. */
public final class SangbanInstrument extends AfroSampleInstrument {
    public SangbanInstrument() { this(ThreadLocalRandom.current()); }
    public SangbanInstrument(RandomGenerator random) { super("sangban", random); }
}
