package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer kensereni_bell multisamples and note mapping. */
public final class KensereniBellInstrument extends AfroSampleInstrument {
    public KensereniBellInstrument() { this(ThreadLocalRandom.current()); }
    public KensereniBellInstrument(RandomGenerator random) { super("kensereni_bell", random); }
}
