package picosoft.javafro.io;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.*;

/** One atomic, versioned record, with optimistic conflict detection across app windows. */
public final class DesktopDraftStore {
    public record Controls(String rhythm, int bpm, List<Double> dialect) {
        public Controls {
            if (rhythm == null || rhythm.isBlank() || bpm < 1 || dialect == null || dialect.isEmpty()
                    || dialect.stream().anyMatch(value -> value == null || !Double.isFinite(value) || Math.abs(value) > 1)) {
                throw new IllegalArgumentException("Neplatné nastavenia konceptu.");
            }
            dialect = List.copyOf(dialect);
        }
    }
    public record Draft(String source, String file, int caret, String savedSource, Controls controls) {
        public Draft {
            if (source == null || file == null || savedSource == null || caret < 0 || caret > source.length()) {
                throw new IllegalArgumentException("Neplatný koncept.");
            }
        }
        public boolean dirty() { return !source.equals(savedSource) || controls != null; }
    }
    private final Path directory, file;
    private String raw;
    private Draft current, previous;

    public DesktopDraftStore(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        this.file = this.directory.resolve("desktop-draft.yaml");
        raw = read();
        if (raw != null) {
            try {
                LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setCodePointLimit(32 * 1024 * 1024);
                Object loaded = new Yaml(new SafeConstructor(options)).load(raw);
                if (!(loaded instanceof Map<?, ?> root) || !Integer.valueOf(1).equals(root.get("version"))) throw new IllegalArgumentException();
                current = decode(root.get("current"));
                previous = root.get("previous") == null ? null : decode(root.get("previous"));
            } catch (RuntimeException exception) { throw new IOException("Poškodený alebo neznámy koncept; uložené dáta neprepisujeme.", exception); }
        }
    }
    public Draft current() { return current; }
    public Draft previous() { return previous; }

    public void save(Draft draft, boolean replace) throws IOException {
        Objects.requireNonNull(draft);
        Files.createDirectories(directory);
        try (FileChannel channel = FileChannel.open(directory.resolve("desktop-draft.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            try (var lock = channel.tryLock()) {
                if (lock == null) throw new IOException("Koncept práve ukladá iné okno.");
                if (!Objects.equals(raw, read())) throw new IOException("Koncept zmenilo iné okno. Ulož svoju prácu do YAML súboru.");
                Draft backup = replace ? current : previous;
                Map<String, Object> root = new LinkedHashMap<>(); root.put("version", 1); root.put("current", encode(draft));
                if (backup != null) root.put("previous", encode(backup));
                String next = new Yaml().dump(root);
                if (next.length() > 32 * 1024 * 1024) throw new IOException("Koncept je príliš veľký. Ulož ho do YAML súboru.");
                Path temporary = Files.createTempFile(directory, "desktop-draft-", ".tmp");
                try {
                    Files.writeString(temporary, next);
                    try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temporary); }
                raw = next; current = draft; previous = backup;
            }
        } catch (OverlappingFileLockException exception) { throw new IOException("Koncept práve ukladá iné okno.", exception); }
    }
    private String read() throws IOException { return Files.exists(file) ? Files.readString(file) : null; }
    private static Map<String, Object> encode(Draft draft) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", draft.source()); map.put("file", draft.file()); map.put("caret", draft.caret()); map.put("savedSource", draft.savedSource());
        if (draft.controls() != null) {
            var controls = draft.controls();
            map.put("controls", Map.of("rhythm", controls.rhythm(), "bpm", controls.bpm(), "dialect", controls.dialect()));
        }
        return map;
    }
    private static Draft decode(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException();
        Controls controls = null;
        if (map.get("controls") != null) {
            var c = (Map<?, ?>) map.get("controls");
            var offsets = ((List<?>) c.get("dialect")).stream().map(number -> ((Number) number).doubleValue()).toList();
            controls = new Controls((String) c.get("rhythm"), (Integer) c.get("bpm"), offsets);
        }
        return new Draft((String) map.get("source"), (String) map.get("file"), (Integer) map.get("caret"), (String) map.get("savedSource"), controls);
    }
}
