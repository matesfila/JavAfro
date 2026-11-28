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
import picosoft.javafro.instruments.sample.matesamples.*;
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

    /**
     * Selects the last top-level rhythm key at or before the caret; leading text selects the first.
     * Only an error in the selected rhythm throws: other broken rhythms do not block it.
     */
    public static Rhythm atCaret(String source, int caretOffset, int defaultBpm) {
        List<Entry> entries = entries(source, defaultBpm);
        if (entries.isEmpty()) {
            load(new java.io.ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)), defaultBpm);
            throw new IllegalArgumentException("Rhythm YAML must contain at least one rhythm.");
        }
        Entry selected = entries.getFirst();
        for (Entry entry : entries) {
            if (entry.offset() > caretOffset) break;
            selected = entry;
        }
        if (selected.rhythm() == null) throw new IllegalArgumentException(selected.error());
        return selected.rhythm();
    }

    /**
     * A top-level rhythm validated on its own: either {@code rhythm} or {@code error} is set. {@code offset}
     * is the UTF-16 index of its key; {@code ranges} are UTF-16 [start, end) pairs to underline: the lines of
     * the faulty YAML node and the rhythm's key line.
     */
    public record Entry(String id, int offset, Rhythm rhythm, String error, List<int[]> ranges) { }

    /** The valid rhythms of a document, in file order; broken rhythms are left out. */
    public static Map<String, Rhythm> valid(String source, int defaultBpm) {
        Map<String, Rhythm> rhythms = new LinkedHashMap<>();
        for (Entry entry : entries(source, defaultBpm)) {
            if (entry.rhythm() != null) rhythms.putIfAbsent(entry.id(), entry.rhythm());
        }
        return Collections.unmodifiableMap(rhythms);
    }

    /**
     * Every top-level rhythm, each validated independently (port of web {@code readRhythmEntries()}).
     * A YAML syntax error or a duplicate key splits the text at top-level keys and parses each part alone.
     * Empty when the document has no top-level map at all.
     */
    public static List<Entry> entries(String source, int defaultBpm) {
        if (source == null || source.isBlank()) return List.of();
        var seen = new java.util.HashSet<String>();
        try {
            return segmentEntries(source, 0, source.length(), defaultBpm, seen);
        } catch (YAMLException wholeDocument) {
            seen.clear();
            List<Entry> entries = new ArrayList<>();
            for (int[] segment : topLevelSegments(source)) {
                try {
                    entries.addAll(segmentEntries(source, segment[0], segment[1], defaultBpm, seen));
                } catch (YAMLException exception) {
                    String part = source.substring(segment[0], segment[1]);
                    String id = segmentKey(part);
                    int at = segment[0];
                    String problem = exception.getMessage();
                    if (exception instanceof org.yaml.snakeyaml.error.MarkedYAMLException marked) {
                        if (marked.getProblemMark() != null) {
                            at = segment[0] + Math.min(offset(part, marked.getProblemMark().getIndex()), Math.max(0, part.length() - 1));
                        }
                        if (marked.getProblem() != null) problem = marked.getProblem();
                    }
                    var ranges = new ArrayList<>(lineRanges(source, at, at));
                    ranges.addAll(lineRanges(source, segment[0], segment[0]));
                    entries.add(new Entry(id, segment[0], null, "Rhythm '" + id + "': Invalid rhythm YAML: " + problem, ranges));
                    seen.add(id);
                }
            }
            return List.copyOf(entries);
        }
    }

    private static List<Entry> segmentEntries(String source, int start, int end, int defaultBpm, Set<String> seen) {
        String part = source.substring(start, end);
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document = new Yaml(new SafeConstructor(options)).load(part);
        if (!(document instanceof Map<?, ?> definitions)) return List.of();
        var root = (org.yaml.snakeyaml.nodes.MappingNode) new Yaml(new SafeConstructor(options)).compose(new java.io.StringReader(part));
        var values = new ArrayList<>(definitions.entrySet());
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < root.getValue().size() && i < values.size(); i++) {
            var tuple = root.getValue().get(i);
            Object key = values.get(i).getKey();
            String id = String.valueOf(key);
            int offset = start + offset(part, tuple.getKeyNode().getStartMark().getIndex());
            List<int[]> keyLine = lineRanges(source, offset, offset);
            try {
                if (seen.contains(id)) throw new IllegalArgumentException("Duplicate rhythm.");
                text(key, "Rhythm identifier");
                Rhythm rhythm = rhythm(id, values.get(i).getValue(), defaultBpm);
                entries.add(new Entry(id, offset, rhythm, null, List.of()));
            } catch (IllegalArgumentException exception) {
                int[] node = seen.contains(id) ? null : locateProblem(id, tuple.getValueNode(), values.get(i).getValue(), defaultBpm);
                var ranges = new ArrayList<int[]>();
                if (node != null) ranges.addAll(lineRanges(source, start + offset(part, node[0]), start + offset(part, node[1])));
                ranges.addAll(keyLine);
                entries.add(new Entry(id, offset, null, "Rhythm '" + id + "': " + exception.getMessage(), List.copyOf(ranges)));
            }
            seen.add(id);
        }
        return entries;
    }

    /** Lines starting at column 0 (top-level keys) begin a segment; leading comments are skipped. */
    private static List<int[]> topLevelSegments(String source) {
        List<Integer> starts = new ArrayList<>();
        for (int line = 0; line < source.length(); ) {
            char first = source.charAt(line);
            if (!Character.isWhitespace(first) && first != '#' && !source.startsWith("---", line) && !source.startsWith("...", line)) starts.add(line);
            int next = source.indexOf('\n', line);
            if (next < 0) break;
            line = next + 1;
        }
        List<int[]> segments = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            segments.add(new int[] {starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : source.length()});
        }
        return segments;
    }

    private static final java.util.regex.Pattern SEGMENT_KEY = java.util.regex.Pattern.compile("^(?:\"([^\"]*)\"|'([^']*)'|([^:#]*?))\\s*:");

    private static String segmentKey(String segment) {
        String line = segment.split("\\r?\\n", 2)[0];
        var match = SEGMENT_KEY.matcher(line);
        if (!match.find()) return line.trim();
        for (int group = 1; group <= 3; group++) if (match.group(group) != null && !match.group(group).isBlank()) return match.group(group).trim();
        return line.trim();
    }

    /** Code-point [start, end) of the YAML node that a definition error comes from, found by validating its parts alone. */
    private static int[] locateProblem(String id, org.yaml.snakeyaml.nodes.Node node, Object definition, int defaultBpm) {
        if (!(node instanceof org.yaml.snakeyaml.nodes.MappingNode fields) || !(definition instanceof Map<?, ?> data)) return range(node);
        for (var field : fields.getValue()) {
            if (!(field.getKeyNode() instanceof org.yaml.snakeyaml.nodes.ScalarNode key) || !RHYTHM_FIELDS.contains(key.getValue())) return range(field);
        }
        Map<Object, Object> metadata = new LinkedHashMap<>();
        for (String name : METADATA_FIELDS) {
            if (!data.containsKey(name)) continue;
            Map<Object, Object> next = new LinkedHashMap<>(metadata);
            next.put(name, data.get(name));
            try { metadata(next, defaultBpm); metadata = next; }
            catch (IllegalArgumentException exception) { return range(field(fields, name)); }
        }
        var tracksField = field(fields, "tracks");
        if (tracksField == null || !(tracksField.getValueNode() instanceof org.yaml.snakeyaml.nodes.MappingNode tracks)
                || !(data.get("tracks") instanceof Map<?, ?> trackValues)) return range(tracksField);
        var values = new ArrayList<>(trackValues.entrySet());
        for (int i = 0; i < tracks.getValue().size() && i < values.size(); i++) {
            var track = tracks.getValue().get(i);
            Object instrument = values.get(i).getKey();
            if (!fails(id, metadata, instrument, values.get(i).getValue(), defaultBpm)) continue;
            if (track.getValueNode() instanceof org.yaml.snakeyaml.nodes.MappingNode details
                    && values.get(i).getValue() instanceof Map<?, ?> detailValues) {
                var base = field(details, "base");
                if (base != null && fails(id, metadata, instrument, detailValues.get("base"), defaultBpm)) return range(base.getValueNode());
                var variations = field(details, "variations");
                if (variations != null && variations.getValueNode() instanceof org.yaml.snakeyaml.nodes.SequenceNode items
                        && detailValues.get("variations") instanceof List<?> patterns) {
                    for (int j = 0; j < items.getValue().size() && j < patterns.size(); j++) {
                        if (fails(id, metadata, instrument, patterns.get(j), defaultBpm)) return range(items.getValue().get(j));
                    }
                }
            }
            return range(track);
        }
        return range(tracksField);
    }

    private static boolean fails(String id, Map<Object, Object> metadata, Object instrument, Object track, int defaultBpm) {
        Map<Object, Object> probe = new LinkedHashMap<>(metadata);
        Map<Object, Object> tracks = new LinkedHashMap<>();
        tracks.put(instrument, track);
        probe.put("tracks", tracks);
        try { rhythm(id, probe, defaultBpm); return false; }
        catch (RuntimeException exception) { return true; }
    }

    private static org.yaml.snakeyaml.nodes.NodeTuple field(org.yaml.snakeyaml.nodes.MappingNode map, String name) {
        for (var tuple : map.getValue()) {
            if (tuple.getKeyNode() instanceof org.yaml.snakeyaml.nodes.ScalarNode key && name.equals(key.getValue())) return tuple;
        }
        return null;
    }

    private static int[] range(org.yaml.snakeyaml.nodes.NodeTuple tuple) {
        return tuple == null ? null : new int[] {tuple.getKeyNode().getStartMark().getIndex(), tuple.getValueNode().getEndMark().getIndex()};
    }

    private static int[] range(org.yaml.snakeyaml.nodes.Node node) {
        return node == null ? null : new int[] {node.getStartMark().getIndex(), node.getEndMark().getIndex()};
    }

    /** Each non-blank line touching [start, end], from its first non-blank character to the line end. */
    private static List<int[]> lineRanges(String source, int start, int end) {
        List<int[]> ranges = new ArrayList<>();
        // A node may end right after its line break; that next line is not part of it.
        int last = end > start ? end - 1 : start;
        int line = start > 0 ? source.lastIndexOf('\n', start - 1) + 1 : 0;
        while (true) {
            int next = source.indexOf('\n', line);
            int lineEnd = next < 0 ? source.length() : next;
            if (lineEnd > line && source.charAt(lineEnd - 1) == '\r') lineEnd--;
            int first = line;
            while (first < lineEnd && Character.isWhitespace(source.charAt(first))) first++;
            if (first < lineEnd) ranges.add(new int[] {first, lineEnd});
            if (next < 0 || next >= last) break;
            line = next + 1;
        }
        return ranges;
    }

    /** Track names that {@link #instrument(String)} accepts, in the order of the web {@code instruments} list. */
    public static final List<String> INSTRUMENTS = List.of(
            "dundun", "sangban", "kensereni", "dundun_withBell", "sangban_withBell", "kensereni_withBell",
            "dundunBell", "sangbanBell", "kensereniBell", "balafon", "djembe", "djembe2",
            "dundun_freesound", "sangban_freesound", "kensereni_freesound");
    private static final Map<String, List<String>> NOTES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.regex.Pattern TRACK_LINE_TAIL = java.util.regex.Pattern.compile("^(?:\\s*(?:#.*)?|:\\s*(?:#.*)?)$");

    /**
     * Track (instrument) whose key or pattern line contains the caret, or {@code null}.
     * Uses only the YAML structure, so it also works while a pattern is invalid (port of web note-context.ts).
     */
    public static String instrumentAtCaret(String source, int caretOffset) {
        org.yaml.snakeyaml.nodes.Node root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(true);
            root = new Yaml(new SafeConstructor(options)).compose(new java.io.StringReader(source));
        } catch (RuntimeException exception) {
            return null;
        }
        if (!(root instanceof org.yaml.snakeyaml.nodes.MappingNode rhythms)) return null;
        for (var rhythm : rhythms.getValue()) {
            if (!(rhythm.getValueNode() instanceof org.yaml.snakeyaml.nodes.MappingNode fields)) continue;
            for (var field : fields.getValue()) {
                if (!(field.getKeyNode() instanceof org.yaml.snakeyaml.nodes.ScalarNode key) || !"tracks".equals(key.getValue())
                        || !(field.getValueNode() instanceof org.yaml.snakeyaml.nodes.MappingNode tracks)) continue;
                for (var track : tracks.getValue()) {
                    if (!(track.getKeyNode() instanceof org.yaml.snakeyaml.nodes.ScalarNode name)
                            || !INSTRUMENTS.contains(name.getValue())) continue;
                    // SnakeYAML marks count code points; the caret is a UTF-16 offset.
                    int keyStart = offset(source, name.getStartMark().getIndex());
                    int lineStart = source.lastIndexOf('\n', keyStart - 1) + 1;
                    int start = source.substring(lineStart, keyStart).isBlank() ? lineStart : keyStart;
                    int end = offset(source, track.getValueNode().getEndMark().getIndex());
                    int lineEnd = source.indexOf('\n', Math.max(start, end - 1));
                    int endOfLine = lineEnd < 0 ? source.length() : lineEnd;
                    // Include the rest of the last pattern line, but never the following track.
                    String tail = endOfLine > end ? source.substring(end, endOfLine) : "";
                    int stop = TRACK_LINE_TAIL.matcher(tail).matches() ? endOfLine : end;
                    if (caretOffset >= start && caretOffset <= stop) return name.getValue();
                }
            }
        }
        return null;
    }

    /** Note symbols of a track name for the note keyboard; empty for an unknown or missing instrument. */
    public static List<String> notesFor(String instrumentId) {
        if (instrumentId == null || !INSTRUMENTS.contains(instrumentId)) return List.of();
        return NOTES.computeIfAbsent(instrumentId, id -> {
            List<String> manifest = AfroSampleInstrument.manifestNotes(id);
            return manifest.isEmpty() ? instrument(id).notes() : manifest;
        });
    }

    private static int offset(String source, int codePoints) {
        int length = source.codePointCount(0, source.length());
        return source.offsetByCodePoints(0, Math.max(0, Math.min(codePoints, length)));
    }

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

    private static final Set<String> RHYTHM_FIELDS = Set.of("bpm", "tracks", "timeSignature", "stepsPerBeat", "dialect", "legacy");
    private static final List<String> METADATA_FIELDS = List.of("bpm", "timeSignature", "stepsPerBeat", "dialect", "legacy");

    private record Metadata(int bpm, TimeSignature timeSignature, int stepsPerBeat, Dialect dialect) { }

    private static Rhythm rhythm(String id, Object value, int defaultBpm) {
        Map<?, ?> definition = mapping(value, "Definition");
        for (Object key : definition.keySet()) {
            if (!RHYTHM_FIELDS.contains(key)) {
                throw new IllegalArgumentException("Unknown rhythm field: " + key);
            }
        }
        Metadata metadata = metadata(definition, defaultBpm);
        Map<?, ?> patterns = mapping(definition.get("tracks"), "Tracks");
        TimeSignature timeSignature = metadata.timeSignature();
        int stepsPerBeat = metadata.stepsPerBeat();
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
                tracks.add(new BeepTrack(base, instrument(instrument), variations, instrument));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Track '" + instrument + "': " + exception.getMessage(), exception);
            }
        }
        return new Rhythm(id, tracks, metadata.bpm(), timeSignature, stepsPerBeat, metadata.dialect());
    }

    private static Metadata metadata(Map<?, ?> definition, int defaultBpm) {
        Object tempo = definition.containsKey("bpm") ? definition.get("bpm") : defaultBpm;
        if (!(tempo instanceof Integer bpm)) {
            throw new IllegalArgumentException("BPM must be an integer.");
        }
        if (bpm <= 0) throw new IllegalArgumentException("BPM must be greater than zero.");
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
        return new Metadata(bpm, timeSignature, stepsPerBeat, dialect);
    }

    private static Instrument instrument(String id) {
        return switch (id) {
            case "dundun" -> new DundunDrumInstrument();
            case "sangban" -> new SangbanDrumInstrument();
            case "kensereni" -> new KensereniDrumInstrument();
            case "dundun_withBell" -> new DundunInstrument();
            case "sangban_withBell" -> new SangbanInstrument();
            case "kensereni_withBell" -> new KensereniInstrument();
            case "dundunBell" -> new DundunAccentBellInstrument();
            case "sangbanBell" -> new SangbanAccentBellInstrument();
            case "kensereniBell" -> new KensereniAccentBellInstrument();
            case "balafon" -> new BalafonInstrument();
            case "djembe2" -> new Djembe2Instrument();
            case "dundun_freesound" -> new picosoft.javafro.instruments.sample.jimmijames.DundunInstrument();
            case "sangban_freesound" -> new picosoft.javafro.instruments.sample.jimmijames.SangbanInstrument();
            case "kensereni_freesound" -> new picosoft.javafro.instruments.sample.jimmijames.KensereniInstrument();
            case "djembe" -> new DjembeInstrument();
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
