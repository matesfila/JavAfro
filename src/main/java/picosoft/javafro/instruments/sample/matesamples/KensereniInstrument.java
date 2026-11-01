package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer kensereni multisamples and note mapping. */
public final class KensereniInstrument extends AfroSampleInstrument {
    public KensereniInstrument() { this(ThreadLocalRandom.current()); }
    public KensereniInstrument(RandomGenerator random) { super("kensereni", random); }
}
