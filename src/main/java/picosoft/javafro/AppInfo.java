package picosoft.javafro;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Application metadata supplied by Maven from pom.xml. */
public final class AppInfo {
    private static final Properties INFO = load();

    private AppInfo() { }

    public static String version() { return INFO.getProperty("version"); }
    public static String creator() { return INFO.getProperty("creator"); }
    public static String assistant() { return INFO.getProperty("assistant"); }

    private static Properties load() {
        try (var input = AppInfo.class.getResourceAsStream("/app-info.properties")) {
            if (input == null) throw new IllegalStateException("Chýbajú informácie o aplikácii.");
            Properties info = new Properties();
            info.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return info;
        } catch (IOException exception) {
            throw new IllegalStateException("Informácie o aplikácii sa nepodarilo načítať.", exception);
        }
    }
}
