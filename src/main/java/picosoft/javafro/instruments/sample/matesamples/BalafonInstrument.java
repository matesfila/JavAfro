package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Seven original AfroPlayer pitches, each with three dyn2 recordings. */
public final class BalafonInstrument extends AfroSampleInstrument {
    public BalafonInstrument() { this(ThreadLocalRandom.current()); }
    public BalafonInstrument(RandomGenerator random) { super("balafon", random); }
}
