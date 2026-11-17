package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer dundunBell multisamples and note mapping. */
public final class DundunAccentBellInstrument extends AfroSampleInstrument {
    public DundunAccentBellInstrument() { this(ThreadLocalRandom.current()); }
    public DundunAccentBellInstrument(RandomGenerator random) { super("dundunBell", random); }
}
