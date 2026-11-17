package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer kensereniBell multisamples and note mapping. */
public final class KensereniAccentBellInstrument extends AfroSampleInstrument {
    public KensereniAccentBellInstrument() { this(ThreadLocalRandom.current()); }
    public KensereniAccentBellInstrument(RandomGenerator random) { super("kensereniBell", random); }
}
