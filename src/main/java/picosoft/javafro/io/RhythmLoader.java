package picosoft.javafro.io;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import picosoft.javafro.base.Instrument;
import picosoft.javafro.base.Rhythm;
import picosoft.javafro.base.Track;
import picosoft.javafro.instruments.DundunInstrument;
import picosoft.javafro.instruments.KensereniInstrument;
import picosoft.javafro.instruments.SangbanInstrument;
import picosoft.javafro.sequencer.BeepTrack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Loads a YAML map of rhythm identifiers to rhythm definitions. */
public final class RhythmLoader {
    private RhythmLoader() { }

    public static Map<String, Rhythm> load(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return load(input);
        }
    }

    /** The caller owns the stream and must close it. */
    public static Map<String, Rhythm> load(InputStream input) {
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
                rhythms.put(id, rhythm(entry.getValue()));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Rhythm '" + id + "': " + exception.getMessage(), exception);
            }
        }
        return Collections.unmodifiableMap(rhythms);
    }

    private static Rhythm rhythm(Object value) {
        Map<?, ?> definition = mapping(value, "Definition");
        for (Object key : definition.keySet()) {
            if (!Set.of("name", "bpm", "tracks").contains(key)) {
                throw new IllegalArgumentException("Unknown rhythm field: " + key);
            }
        }
        String name = text(definition.get("name"), "Name");
        if (!(definition.get("bpm") instanceof Integer bpm)) {
            throw new IllegalArgumentException("BPM must be an integer.");
        }
        Map<?, ?> patterns = mapping(definition.get("tracks"), "Tracks");
        var tracks = new ArrayList<Track>();
        for (var entry : patterns.entrySet()) {
            String instrument = text(entry.getKey(), "Instrument");
            try {
                tracks.add(new BeepTrack(text(entry.getValue(), "Pattern"), instrument(instrument)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Track '" + instrument + "': " + exception.getMessage(), exception);
            }
        }
        return new Rhythm(name, tracks, bpm);
    }

    private static Instrument instrument(String id) {
        return switch (id) {
            case "dundun" -> new DundunInstrument();
            case "sangban" -> new SangbanInstrument();
            case "kensereni" -> new KensereniInstrument();
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
