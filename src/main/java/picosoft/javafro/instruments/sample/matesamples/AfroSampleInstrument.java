package picosoft.javafro.instruments.sample.matesamples;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import picosoft.javafro.base.AudioConstants;
import picosoft.javafro.base.Instrument;
import picosoft.javafro.config.AppConfig;
import picosoft.javafro.instruments.sample.WavInstrument;
import picosoft.javafro.instruments.sample.WavSampleInstrument;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/** Original AfroPlayer multisamples. Each hit owns immutable drum/bell voices and their full tails. */
public abstract class AfroSampleInstrument implements Instrument {
    private record Bank(List<String> files, double gain, double rate) {}
    private record Note(String bank, double probability, double gain) {}
    private record Definition(String bell, Map<Character, Note> notes) {}
    private record Manifest(Map<String, Bank> banks, Map<String, Definition> instruments) {}
    private static final class Resources { static final Manifest MANIFEST = load(); }
    private static final Map<String, List<WavInstrument>> DECODED = new ConcurrentHashMap<>();
    private final Definition definition;
    private final RandomGenerator random;
    private final AppConfig config;

    protected AfroSampleInstrument(String id, RandomGenerator random) {
        this(id, random, loadConfig());
    }

    protected AfroSampleInstrument(String id, RandomGenerator random, AppConfig config) {
        this.config = Objects.requireNonNull(config);
        this.random = Objects.requireNonNull(random);
        definition = Objects.requireNonNull(Resources.MANIFEST.instruments().get(id), "Unknown AfroPlayer instrument: " + id);
        // Decode needed banks before the audio clock starts; other instruments stay unloaded.
        for (Note note : definition.notes().values()) decoded(note.bank());
        if (definition.bell() != null) decoded(definition.bell());
    }

    private static Manifest load() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (var stream = AfroSampleInstrument.class.getResourceAsStream("/samples/afroplayer-samples.yaml")) {
            if (stream == null) throw new IllegalArgumentException("Missing AfroPlayer sample manifest.");
            Map<?, ?> root = new Yaml(new SafeConstructor(options)).load(stream);
            Map<String, Bank> banks = new LinkedHashMap<>();
            for (var entry : ((Map<?, ?>) root.get("banks")).entrySet()) {
                Map<?, ?> bank = (Map<?, ?>) entry.getValue();
                var files = ((List<?>) bank.get("samples")).stream().map(String.class::cast).toList();
                if (files.isEmpty()) throw new IllegalArgumentException("Empty sample bank: " + entry.getKey());
                banks.put((String) entry.getKey(), new Bank(files, number(bank, "gain", 0, Double.MAX_VALUE),
                        number(bank, "rate", Double.MIN_NORMAL, Double.MAX_VALUE)));
            }
            Map<String, Definition> instruments = new LinkedHashMap<>();
            for (var entry : ((Map<?, ?>) root.get("instruments")).entrySet()) {
                Map<?, ?> instrument = (Map<?, ?>) entry.getValue();
                Map<Character, Note> notes = new LinkedHashMap<>();
                for (var n : ((Map<?, ?>) instrument.get("notes")).entrySet()) {
                    String symbol = (String) n.getKey();
                    if (symbol.length() != 1) throw new IllegalArgumentException("Expected one note symbol: " + symbol);
                    Map<?, ?> note = (Map<?, ?>) n.getValue();
                    String bank = (String) note.get("bank");
                    if (!banks.containsKey(bank)) throw new IllegalArgumentException("Unknown sample bank: " + bank);
                    notes.put(symbol.charAt(0), new Note(bank, number(note, "probability", 0, 1), number(note, "gain", 0, Double.MAX_VALUE)));
                }
                String bell = (String) instrument.get("bell");
                if (notes.isEmpty() || (bell != null && !banks.containsKey(bell))) {
                    throw new IllegalArgumentException("Missing notes or invalid bell bank: " + entry.getKey());
                }
                instruments.put((String) entry.getKey(), new Definition(bell, Map.copyOf(notes)));
            }
            return new Manifest(Map.copyOf(banks), Map.copyOf(instruments));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Cannot read AfroPlayer sample manifest.", exception);
        }
    }

    private static AppConfig loadConfig() {
        try {
            return AppConfig.load();
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Cannot read application configuration.", exception);
        }
    }

    private static double number(Map<?, ?> map, String key, double min, double max) {
        if (!(map.get(key) instanceof Number value) || !Double.isFinite(value.doubleValue())
                || value.doubleValue() < min || value.doubleValue() > max) {
            throw new IllegalArgumentException("Invalid sample parameter: " + key);
        }
        return value.doubleValue();
    }

    private static List<WavInstrument> decoded(String bank) {
        return DECODED.computeIfAbsent(bank, id -> Resources.MANIFEST.banks().get(id).files().stream()
                .map(file -> WavSampleInstrument.loadSample("/samples/" + file)).toList());
    }

    @Override public final boolean supportsNote(char note) { return definition.notes().containsKey(note); }

    @Override public final Instrument voice(char symbol, int humanize) {
        if (humanize < 0 || humanize > AudioConstants.HUMANIZE_PERCENT_MAX) throw new IllegalArgumentException("Humanize must be 0–100.");
        Note note = definition.notes().get(symbol);
        if (note == null) throw new IllegalArgumentException("Unsupported AfroPlayer note: " + symbol);
        if (note.probability() < 1 && random.nextDouble() >= note.probability()) return null;
        return makeVoice(symbol, humanize);
    }

    private Instrument makeVoice(char symbol, int humanize) {
        Note note = definition.notes().get(symbol);
        Instrument main = bankVoice(note.bank(), note.gain(), humanize);
        if (definition.bell() == null || symbol == 'b') return main;
        Instrument bell = bankVoice(definition.bell(), 1, humanize);
        return new Instrument() {
            public double sample(long index) { return main.sample(index) + bell.sample(index); }
            public long lengthSamples(long pulse) { return Math.max(main.lengthSamples(pulse), bell.lengthSamples(pulse)); }
        };
    }

    private Instrument bankVoice(String id, double noteGain, int humanize) {
        Bank bank = Resources.MANIFEST.banks().get(id);
        List<WavInstrument> samples = decoded(id);
        WavInstrument sample = samples.get(random.nextInt(samples.size()));
        double amount = humanize / (double) AudioConstants.HUMANIZE_PERCENT_MAX;
        double gain = bank.gain() * noteGain + variation(config.humanizeDynamic(), amount);
        double rate = bank.rate() + variation(config.humanizePitch(), amount);
        return new Instrument() {
            public double sample(long index) {
                if (index < 0) return 0;
                double position = index * rate;
                long left = (long) position;
                double fraction = position - left;
                return (sample.sample(left) * (1 - fraction) + sample.sample(left + 1) * fraction) * gain;
            }
            public long lengthSamples(long pulse) { return (long) Math.ceil(sample.lengthSamples(pulse) / rate); }
        };
    }

    private double variation(double limit, double amount) {
        return amount == 0 ? 0 : (random.nextDouble() * 2 - 1) * limit * amount;
    }

    private UnsupportedOperationException noteRequired() {
        return new UnsupportedOperationException("Select a note with voice(note, humanize) before rendering or playing this instrument.");
    }

    @Override public final double sample(long index) { throw noteRequired(); }
    @Override public final long lengthSamples(long pulse) { throw noteRequired(); }
    @Override public final void playTone(int durationMs) { throw noteRequired(); }
}
