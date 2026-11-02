import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import javax.swing.SwingUtilities;

/** Checks the actual distribution JAR, isolated from target/classes. Run from the project root. */
public class ObfuscationSmokeCheck {
    public static void main(String[] args) {
        try {
            verify(args.length == 0 ? Path.of("target/JavAfro-obfuscated.jar") : Path.of(args[0]));
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
        // The editor starts Swing timers; this standalone check must terminate them.
        System.exit(0);
    }

    private static void verify(Path distributionJar) throws Exception {
        Path target = Path.of("target");
        Map<String, String> classes = new HashMap<>();
        Map<String, String> methods = new HashMap<>();
        String owner = "";
        for (String line : Files.readAllLines(target.resolve("proguard-mapping.txt"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] pair = line.trim().split(" -> ", 2);
            if (!line.startsWith(" ")) {
                owner = pair[0];
                classes.put(owner, pair[1].replace(":", ""));
            } else if (pair[0].contains("(")) {
                methods.put(owner + "#" + pair[0].substring(pair[0].indexOf(' ') + 1), pair[1]);
            }
        }

        try (var original = new JarFile(target.resolve("JavAfro.jar").toFile());
             var jar = new JarFile(distributionJar.toFile());
             var loader = new URLClassLoader(new java.net.URL[]{
                     distributionJar.toUri().toURL()
             }, ClassLoader.getPlatformClassLoader())) {
            check("picosoft.javafro.AfroJavaPlayer".equals(
                    jar.getManifest().getMainAttributes().getValue("Main-Class")), "Main-Class missing");
            int renamed = 0;
            for (var entry : classes.entrySet()) {
                if (entry.getKey().startsWith("picosoft.javafro.") && !entry.getKey().equals(entry.getValue())) {
                    check(jar.getJarEntry(entry.getKey().replace('.', '/') + ".class") == null,
                            "Original class still present: " + entry.getKey());
                    check(jar.getJarEntry(entry.getValue().replace('.', '/') + ".class") != null,
                            "Renamed class missing: " + entry.getValue());
                    renamed++;
                }
            }
            check(renamed > 0, "No application classes were renamed");
            int resources = 0;
            for (var entry : original.stream().filter(e -> !e.isDirectory()
                    && !e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")).toList()) {
                var copy = jar.getJarEntry(entry.getName());
                check(copy != null, "Missing resource: " + entry.getName());
                try (var before = original.getInputStream(entry); var after = jar.getInputStream(copy)) {
                    check(java.util.Arrays.equals(before.readAllBytes(), after.readAllBytes()),
                            "Changed resource: " + entry.getName());
                }
                resources++;
            }
            Class<?> config = loader.loadClass(classes.get("picosoft.javafro.config.AppConfig"));
            check(method(config, methods, "picosoft.javafro.config.AppConfig", "load()").invoke(null) != null,
                    "Configuration failed");
            Class<?> rhythms = loader.loadClass(classes.get("picosoft.javafro.io.RhythmLoader"));
            Method load = method(rhythms, methods, "picosoft.javafro.io.RhythmLoader",
                    "load(java.nio.file.Path)", Path.class);
            // Loading constructs instruments and decodes their bundled WAV samples.
            for (String file : new String[]{"basicRhythms.yaml", "afroplayer-instruments.yaml", "djembe-demo.yaml"}) {
                check(!((Map<?, ?>) load.invoke(null, Path.of("rhythms", file))).isEmpty(),
                        "Rhythm load failed: " + file);
            }
            String yaml = Files.readString(Path.of("rhythms/basicRhythms.yaml"));
            SwingUtilities.invokeAndWait(() -> {
                try {
                    var constructor = loader.loadClass(classes.get("picosoft.javafro.ui.RhythmPlayerPanel"))
                            .getDeclaredConstructor(String.class);
                    constructor.setAccessible(true);
                    Container panel = (Container) constructor.newInstance(yaml);
                    Component editor = findEditor(panel);
                    check(editor != null, "YAML editor missing");
                    check(yaml.equals(editor.getClass().getMethod("getText").invoke(editor)), "YAML text changed");
                    Object folds = editor.getClass().getMethod("getFoldManager").invoke(editor);
                    check((int) folds.getClass().getMethod("getFoldCount").invoke(folds) > 0,
                            "YAML folding failed");
                } catch (Exception exception) {
                    throw new AssertionError("Obfuscated Swing panel failed", exception);
                }
            });
            System.out.println("PASS: " + renamed + " renamed classes, " + resources
                    + " unchanged resources, configuration, rhythms, samples and Swing YAML editor.");
        }
    }

    private static Method method(Class<?> type, Map<String, String> methods, String owner,
                                 String signature, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(methods.get(owner + "#" + signature), parameters);
        method.setAccessible(true);
        return method;
    }

    private static Component findEditor(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child.getClass().getName().equals("org.fife.ui.rsyntaxtextarea.RSyntaxTextArea")) return child;
            if (child instanceof Container container) {
                Component editor = findEditor(container);
                if (editor != null) return editor;
            }
        }
        return null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
