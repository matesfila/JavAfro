package picosoft.javafro.base;

/**
 * Jednoduchý nástroj, ktorý vytvára sínusový tón so zadanou frekvenciou v Hz.
 * Metóda {@link #sample(long)} počíta amplitúdu jednej zvukovej vzorky pri
 * vzorkovacej frekvencii 44 100 Hz ({@link AudioConstants#SAMPLE_RATE}).
 *
 * <p>Úder trvá najviac 50 ms alebo polovicu pulzu a jeho okraje vyhladzuje
 * sínusová obálka, aby pri začiatku a konci úderu nevznikali kliknutia.
 * Prehrávanie samostatného tónu ({@link #playTone(int)}) zdedil z {@link Instrument}.
 * Potomkovia môžu nastaviť inú frekvenciu alebo prepísať výpočet vzorky
 * a zmeniť tak charakter zvuku.</p>
 */
public class BeepInstrument implements Instrument {
    public static final int DEFAULT_FREQUENCY = 880;
    private static final int MAX_BEEP_DURATION_MS = 50;
    private static final int MAX_BEEP_SAMPLES = AudioConstants.SAMPLE_RATE * MAX_BEEP_DURATION_MS / AudioConstants.MILLISECONDS_PER_SECOND;
    private static final int PULSE_DURATION_DIVISOR = 2;
    private final double frequencyHz;

    public BeepInstrument() {
        this(DEFAULT_FREQUENCY);
    }

    public BeepInstrument(double frequencyHz) {
        if (!Double.isFinite(frequencyHz) || frequencyHz <= 0 || frequencyHz >= AudioConstants.SAMPLE_RATE / 2.0) {
            throw new IllegalArgumentException("Frequency must be greater than zero and below " + AudioConstants.SAMPLE_RATE / 2 + " Hz.");
        }
        this.frequencyHz = frequencyHz;
    }

    public double frequencyHz() {
        return frequencyHz;
    }

    @Override
    public double sample(long sampleIndex) {
        return Math.sin(2.0 * Math.PI * frequencyHz * sampleIndex / AudioConstants.SAMPLE_RATE);
    }

    @Override
    public long lengthSamples(long pulseSamples) {
        return Math.min(MAX_BEEP_SAMPLES, pulseSamples / PULSE_DURATION_DIVISOR);
    }

    @Override
    public double envelope(long sampleIndex, long lengthSamples) {
        return Math.sin(Math.PI * sampleIndex / lengthSamples);
    }
}
