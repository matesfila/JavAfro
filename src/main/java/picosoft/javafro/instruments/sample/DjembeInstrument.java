package picosoft.javafro.instruments.sample;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import picosoft.javafro.base.Instrument;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Tomas Marek multisamples from AfroPlayer. All randomness is selected once per voice. */
public final class DjembeInstrument implements Instrument {
    private record Note(List<WavInstrument> samples, double gain, double probability) {}
    private record Bank(Map<Character, Note> notes, double dynamic, double pitch, double outputGain) {}
    private static final class Resources { private static final Bank BANK = load(); }
    private final RandomGenerator random;

    public DjembeInstrument() { this(ThreadLocalRandom.current()); }
    public DjembeInstrument(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
        // Decode once, before the sequencer starts its audio clock.
        Objects.requireNonNull(Resources.BANK);
    }

    private static Bank load() {
        try (var stream = DjembeInstrument.class.getResourceAsStream("/samples/djembe.json")) {
            if (stream == null) throw new IllegalArgumentException("Missing djembe sample manifest.");
            Map<?, ?> manifest = new Yaml(new SafeConstructor(new LoaderOptions())).load(stream);
            Map<?, ?> definitions = (Map<?, ?>) manifest.get("banks");
            Map<String, List<WavInstrument>> banks = new HashMap<>();
            for (var entry : definitions.entrySet()) {
                var samples = ((List<?>) entry.getValue()).stream()
                        .map(file -> WavSampleInstrument.loadSample("/samples/" + file)).toList();
                banks.put((String) entry.getKey(), samples);
            }
            Map<Character, Note> notes = new HashMap<>();
            for (var entry : ((Map<?, ?>) manifest.get("notes")).entrySet()) {
                Map<?, ?> note = (Map<?, ?>) entry.getValue();
                notes.put(((String) entry.getKey()).charAt(0), new Note(banks.get((String) note.get("bank")),
                        ((Number) note.get("gain")).doubleValue(), ((Number) note.get("probability")).doubleValue()));
            }
            return new Bank(Map.copyOf(notes), ((Number) manifest.get("dynamic")).doubleValue(),
                    ((Number) manifest.get("pitch")).doubleValue(), ((Number) manifest.get("outputGain")).doubleValue());
        } catch (IOException exception) { throw new IllegalArgumentException("Cannot read djembe manifest.", exception); }
    }

    @Override public boolean supportsNote(char note) { return Resources.BANK.notes().containsKey(note); }

    @Override public Instrument voice(char symbol, int humanize) {
        if (humanize < 0 || humanize > 100) throw new IllegalArgumentException("Humanize must be 0–100.");
        Note note = Resources.BANK.notes().get(symbol);
        if (note == null) throw new IllegalArgumentException("Unknown djembe note: " + symbol);
        if (note.probability() < 1 && random.nextDouble() >= note.probability()) return null;
        var sample = note.samples().get(random.nextInt(note.samples().size()));
        double amount = humanize / 100.0;
        double gain = note.gain() + (amount == 0 ? 0 : (random.nextDouble() * 2 - 1) * Resources.BANK.dynamic() * amount);
        double rate = 1 + (amount == 0 ? 0 : (random.nextDouble() * 2 - 1) * Resources.BANK.pitch() * amount);
        return new Instrument() {
            @Override public double sample(long index) {
                if (index < 0) return 0;
                double position = index * rate;
                long left = (long) position;
                double fraction = position - left;
                return (sample.sample(left) * (1 - fraction) + sample.sample(left + 1) * fraction) * gain * Resources.BANK.outputGain();
            }
            @Override public long lengthSamples(long pulseSamples) { return (long) Math.ceil(sample.lengthSamples(pulseSamples) / rate); }
        };
    }

    // Direct playTone/sample uses a stable bass; sequencer playback calls voice() for each hit.
    @Override public double sample(long index) { return Resources.BANK.notes().get('D').samples().getFirst().sample(index) * Resources.BANK.outputGain(); }
    @Override public long lengthSamples(long pulseSamples) { return Resources.BANK.notes().get('D').samples().getFirst().lengthSamples(pulseSamples); }
}
