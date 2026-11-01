package picosoft.javafro.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import picosoft.javafro.base.AudioConstants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Application defaults, separate from each rhythm's musical settings. */
public record AppConfig(int humanize, int humanizeMaxOffsetMs, int initialTempo, int minimumTempo, int maximumTempo,
                        Path rhythmsDirectory, String initialRhythm, int editorFontSize,
                        int windowWidth, int windowHeight) {
    public static AppConfig defaults() {
        return new AppConfig(21, AudioConstants.DEFAULT_HUMANIZE_MAX_OFFSET_MS, 116, 20, 300, Path.of("rhythms"), "basicRhythms.yaml", 16, 1200, 900);
    }

    public static AppConfig load() throws IOException {
        Path local = Path.of("javafro.yaml").toAbsolutePath();
        if (Files.exists(local)) return load(local);
        try {
            Path code = Path.of(AppConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(code)) {
                Path besideJar = code.getParent().resolve("javafro.yaml");
                if (Files.exists(besideJar)) return load(besideJar);
                // jpackage puts the JAR in app/ beside the launcher directory.
                if (code.getParent().getFileName().toString().equals("app")) {
                    Path besideLauncher = code.getParent().getParent().resolve("javafro.yaml");
                    if (Files.exists(besideLauncher)) return load(besideLauncher);
                }
            }
        } catch (java.net.URISyntaxException exception) {
            throw new IOException("Nepodarilo sa určiť priečinok aplikácie.", exception);
        }
        try (InputStream input = AppConfig.class.getResourceAsStream("/javafro.yaml")) {
            return input == null ? defaults() : parse(input, Path.of(".").toAbsolutePath());
        }
    }

    public static AppConfig load(Path file) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        try (InputStream input = Files.newInputStream(absolute)) {
            return parse(input, absolute.getParent());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Konfigurácia " + absolute + ": " + exception.getMessage(), exception);
        }
    }

    public static AppConfig parse(InputStream input, Path baseDirectory) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document;
        try {
            document = new Yaml(new SafeConstructor(options)).load(input);
        } catch (org.yaml.snakeyaml.error.YAMLException exception) {
            throw new IllegalArgumentException("Neplatný konfiguračný YAML: " + exception.getMessage(), exception);
        }
        Map<?, ?> root = document == null ? Map.of() : mapping(document, "konfigurácia");
        keys(root, "konfigurácia", "playback", "library", "editor", "window");
        Map<?, ?> playback = section(root, "playback", "humanize", "humanizeMaxOffsetMs", "tempo");
        Map<?, ?> tempo = section(playback, "tempo", "initial", "minimum", "maximum");
        Map<?, ?> library = section(root, "library", "directory", "initialRhythm");
        Map<?, ?> editor = section(root, "editor", "fontSize");
        Map<?, ?> window = section(root, "window", "width", "height");
        AppConfig defaults = defaults();
        int minimum = number(tempo, "minimum", defaults.minimumTempo, 1, 10000);
        int maximum = number(tempo, "maximum", defaults.maximumTempo, 1, 10000);
        int initial = number(tempo, "initial", defaults.initialTempo, 1, 10000);
        if (minimum > maximum || initial < minimum || initial > maximum) {
            throw new IllegalArgumentException("Tempo musí spĺňať minimum <= initial <= maximum.");
        }
        return new AppConfig(number(playback, "humanize", defaults.humanize, 0, AudioConstants.HUMANIZE_PERCENT_MAX),
                number(playback, "humanizeMaxOffsetMs", defaults.humanizeMaxOffsetMs, 0, AudioConstants.MAX_HUMANIZE_OFFSET_MS), initial, minimum, maximum,
                baseDirectory.resolve(text(library, "directory", "rhythms")).normalize(),
                text(library, "initialRhythm", defaults.initialRhythm),
                number(editor, "fontSize", defaults.editorFontSize, 8, 72),
                number(window, "width", defaults.windowWidth, 760, 10000),
                number(window, "height", defaults.windowHeight, 560, 10000));
    }

    private static Map<?, ?> mapping(Object value, String name) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(name + ": očakáva sa YAML mapa.");
        return map;
    }

    private static Map<?, ?> section(Map<?, ?> parent, String name, String... allowed) {
        Map<?, ?> map = parent.containsKey(name) ? mapping(parent.get(name), name) : Map.of();
        keys(map, name, allowed);
        return map;
    }

    private static void keys(Map<?, ?> map, String name, String... allowed) {
        Set<String> known = Set.of(allowed);
        for (Object key : map.keySet()) if (!known.contains(key)) {
            throw new IllegalArgumentException(name + ": neznáme nastavenie " + key);
        }
    }

    private static int number(Map<?, ?> map, String key, int fallback, int min, int max) {
        if (!map.containsKey(key)) return fallback;
        Object value = map.get(key);
        if (!(value instanceof Integer result) || result < min || result > max) {
            throw new IllegalArgumentException(key + ": očakáva sa celé číslo " + min + "–" + max + ".");
        }
        return result;
    }

    private static String text(Map<?, ?> map, String key, String fallback) {
        if (!map.containsKey(key)) return fallback;
        if (!(map.get(key) instanceof String result) || result.isBlank()) {
            throw new IllegalArgumentException(key + ": očakáva sa neprázdny text.");
        }
        return result;
    }
}
