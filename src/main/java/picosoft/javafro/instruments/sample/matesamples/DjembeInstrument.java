package picosoft.javafro.instruments.sample.matesamples;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Tomáš Marek djembe multisamples and note mapping from the shared AfroPlayer manifest. */
public final class DjembeInstrument extends AfroSampleInstrument {
    public DjembeInstrument() { this(ThreadLocalRandom.current()); }
    public DjembeInstrument(RandomGenerator random) { super("djembe", random); }
}
