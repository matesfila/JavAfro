package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Original AfroPlayer djembe2 multisamples and note mapping. */
public final class Djembe2Instrument extends AfroSampleInstrument {
    public Djembe2Instrument() { this(ThreadLocalRandom.current()); }
    public Djembe2Instrument(RandomGenerator random) { super("djembe2", random); }
}
