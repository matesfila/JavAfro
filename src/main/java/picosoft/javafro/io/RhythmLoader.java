package picosoft.javafro.io;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import picosoft.javafro.base.Instrument;
import picosoft.javafro.base.Rhythm;
import picosoft.javafro.base.Track;
import picosoft.javafro.base.TimeSignature;
import picosoft.javafro.base.Dialect;
import picosoft.javafro.config.AppConfig;
import picosoft.javafro.instruments.sample.DundunInstrument;
import picosoft.javafro.instruments.sample.SangbanInstrument;
import picosoft.javafro.instruments.sample.KensereniInstrument;
import picosoft.javafro.sequencer.BeepTrack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** Loads a YAML map of rhythm identifiers to rhythm definitions. */
public final class RhythmLoader {
    private RhythmLoader() { }

    public static Map<String, Rhythm> load(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return load(input);
        }
    }

    public static Map<String, Rhythm> load(Path path, int defaultBpm) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return load(input, defaultBpm);
        }
    }

    /** The caller owns the stream and must close it. */
    public static Map<String, Rhythm> load(InputStream input) {
        try {
            return load(input, AppConfig.load().initialTempo());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot load global tempo from javafro.yaml.", exception);
        }
    }

    /** Missing BPM uses the supplied application tempo; explicit invalid values still fail. */
    public static Map<String, Rhythm> load(InputStream input, int defaultBpm) {
        if (defaultBpm <= 0) throw new IllegalArgumentException("Default BPM must be greater than zero.");
        if (input == null) {
            throw new IllegalArgumentException("Rhythm YAML input must not be null.");
        }
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document;
        try {
            document = new Yaml(new SafeConstructor(options)).load(input);
        } catch (YAMLException exception) {
            throw new IllegalArgumentException("Invalid rhythm YAML: " + exception.getMessage(), exception);
        }
        Map<?, ?> definitions = mapping(document, "Rhythm YAML");
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("Rhythm YAML must contain at least one rhythm.");
        }
        Map<String, Rhythm> rhythms = new LinkedHashMap<>();
        for (var entry : definitions.entrySet()) {
            String id = text(entry.getKey(), "Rhythm identifier");
            try {
                rhythms.put(id, rhythm(id, entry.getValue(), defaultBpm));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Rhythm '" + id + "': " + exception.getMessage(), exception);
            }
        }
        return Collections.unmodifiableMap(rhythms);
    }

    private static Rhythm rhythm(String id, Object value, int defaultBpm) {
        Map<?, ?> definition = mapping(value, "Definition");
        for (Object key : definition.keySet()) {
            if (!Set.of("bpm", "tracks", "timeSignature", "stepsPerBeat", "dialect", "legacy").contains(key)) {
                throw new IllegalArgumentException("Unknown rhythm field: " + key);
            }
        }
        Object tempo = definition.containsKey("bpm") ? definition.get("bpm") : defaultBpm;
        if (!(tempo instanceof Integer bpm)) {
            throw new IllegalArgumentException("BPM must be an integer.");
        }
        Map<?, ?> patterns = mapping(definition.get("tracks"), "Tracks");
        TimeSignature timeSignature = definition.containsKey("timeSignature")
                ? TimeSignature.parse(text(definition.get("timeSignature"), "Time signature"))
                : new TimeSignature(4, 4);
        int stepsPerBeat = timeSignature.stepsPerBeat();
        if (definition.containsKey("stepsPerBeat")) {
            if (!(definition.get("stepsPerBeat") instanceof Integer steps)) {
                throw new IllegalArgumentException("Steps per beat must be an integer.");
            }
            stepsPerBeat = steps;
        }
        timeSignature.stepsPerBar(stepsPerBeat);
        Dialect dialect = Dialect.straight(stepsPerBeat);
        if (definition.containsKey("dialect")) {
            if (!(definition.get("dialect") instanceof List<?> values)) {
                throw new IllegalArgumentException("Dialect must be a list of pulse offsets between -1 and 1.");
            }
            var gaps = new ArrayList<Number>();
            for (Object gap : values) {
                if (!(gap instanceof Number number)) {
                    throw new IllegalArgumentException("Dialect pulse offsets must be numbers.");
                }
                gaps.add(number);
            }
            dialect = Dialect.of(stepsPerBeat, gaps);
        }
        if (definition.containsKey("legacy")) {
            mapping(definition.get("legacy"), "Legacy archive");
        }
        var tracks = new ArrayList<Track>();
        for (var entry : patterns.entrySet()) {
            String instrument = text(entry.getKey(), "Instrument");
            try {
                Object patternValue = entry.getValue();
                List<String> variations = new ArrayList<>();
                if (patternValue instanceof Map<?, ?> details) {
                    for (Object key : details.keySet()) {
                        if (!Set.of("base", "variations").contains(key)) {
                            throw new IllegalArgumentException("Unknown track field: " + key);
                        }
                    }
                    patternValue = details.get("base");
                    if (details.containsKey("variations")) {
                        if (!(details.get("variations") instanceof List<?> alternatives)) {
                            throw new IllegalArgumentException("Variations must be a list of patterns.");
                        }
                        for (Object alternative : alternatives) {
                            String pattern = text(alternative, "Variation");
                            timeSignature.validatePattern(pattern, stepsPerBeat);
                            variations.add(pattern);
                        }
                    }
                }
                String base = text(patternValue, "Pattern");
                timeSignature.validatePattern(base, stepsPerBeat);
                tracks.add(new BeepTrack(base, instrument(instrument), variations));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Track '" + instrument + "': " + exception.getMessage(), exception);
            }
        }
        return new Rhythm(id, tracks, bpm, timeSignature, stepsPerBeat, dialect);
    }

    private static Instrument instrument(String id) {
        return switch (id) {
//            case "dundun" -> new picosoft.javafro.instruments.synth.DundunInstrument();
//            case "sangban" -> new picosoft.javafro.instruments.synth.SangbanInstrument();
//            case "kensereni" -> new picosoft.javafro.instruments.synth.KensereniInstrument();
            case "dundun" -> new DundunInstrument();
            case "sangban" -> new SangbanInstrument();
            case "kensereni" -> new KensereniInstrument();
            case "djembe" -> new picosoft.javafro.instruments.sample.DjembeInstrument();
            default -> throw new IllegalArgumentException("Unknown instrument: " + id);
        };
    }

    private static Map<?, ?> mapping(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(field + " must be a mapping.");
        }
        return map;
    }

    private static String text(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string.");
        }
        return text;
    }
}
