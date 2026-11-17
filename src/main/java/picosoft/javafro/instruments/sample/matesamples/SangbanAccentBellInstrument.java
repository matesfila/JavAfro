package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer sangbanBell multisamples and note mapping. */
public final class SangbanAccentBellInstrument extends AfroSampleInstrument {
    public SangbanAccentBellInstrument() { this(ThreadLocalRandom.current()); }
    public SangbanAccentBellInstrument(RandomGenerator random) { super("sangbanBell", random); }
}
