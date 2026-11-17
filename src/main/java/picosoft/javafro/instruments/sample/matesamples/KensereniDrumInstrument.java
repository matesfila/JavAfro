package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer kensereni multisamples without a bell. */
public final class KensereniDrumInstrument extends AfroSampleInstrument {
    public KensereniDrumInstrument() { this(ThreadLocalRandom.current()); }
    public KensereniDrumInstrument(RandomGenerator random) { super("kensereni", random); }
}
