package picosoft.javafro.config;

import org.junit.Test;
import picosoft.javafro.ui.RhythmPlayerPanel;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class AppConfigTest {
    private AppConfig parse(String yaml) {
        return AppConfig.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                Path.of("target", "config-test").toAbsolutePath());
    }

    @Test
    public void partialConfigurationKeepsDefaultsAndResolvesLibraryRelativeToConfig() {
        AppConfig config = parse("playback:\n  humanize: 37\nlibrary:\n  directory: moje-rytmy\n");
        assertEquals(37, config.humanize());
        assertEquals(30, config.humanizeMaxOffsetMs());
        assertEquals(0, parse("playback: {humanizeMaxOffsetMs: 0}").humanizeMaxOffsetMs());
        assertEquals(12, parse("playback: {humanizeMaxOffsetMs: 12}").humanizeMaxOffsetMs());
        assertEquals(116, config.initialTempo());
        assertEquals(20, config.minimumTempo());
        assertEquals(300, config.maximumTempo());
        assertEquals(Path.of("target", "config-test", "moje-rytmy").toAbsolutePath(), config.rhythmsDirectory());
        assertEquals(16, parse("").editorFontSize());
    }

    @Test
    public void projectConfigurationLoads() throws Exception {
        AppConfig config = AppConfig.load(Path.of("javafro.yaml"));
        assertTrue(config.humanize() >= 0 && config.humanize() <= 100);
        assertTrue(config.rhythmsDirectory().isAbsolute());
    }

    @Test
    public void rejectsTyposDuplicateKeysWrongTypesAndInvalidRanges() {
        for (String yaml : new String[]{"unknown: 1", "playback: null", "playback: {humanize: 101}",
                "playback: {humanize: -1}", "playback: {humanize: '21'}", "playback: {humanize: 21.5}",
                "playback: {humanize: 1, humanize: 2}", "editor: {fontSize: 0}", "window: {width: 100}",
                "library: {directory: ''}", "playback: {tempo: {minimum: 200, maximum: 100}}",
                "playback: {tempo: {initial: 301}}", "playback: {humanizeMaxOffsetMs: -1}",
                "playback: {humanizeMaxOffsetMs: 1001}", "playback: {humanizeMaxOffsetMs: '30'}",
                "playback: {humanizeMaxOffsetMs: 1.5}", "[]", "playback: ["}) {
            assertThrows(yaml, IllegalArgumentException.class, () -> parse(yaml));
        }
    }

    @Test
    public void playerUsesConfiguredControlsAndKeepsRhythmTempo() throws Exception {
        AppConfig config = parse("playback:\n  humanize: 37\n  tempo: {initial: 90, minimum: 40, maximum: 100}\neditor: {fontSize: 19}");
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel("", config);
            assertEquals(37, ((JSlider) find(panel, "humanizeSlider")).getValue());
            JSlider tempo = (JSlider) find(panel, "tempoSlider");
            assertEquals(40, tempo.getMinimum());
            assertEquals(100, tempo.getMaximum());
            assertEquals(90, tempo.getValue());
            assertEquals(19, find(panel, "yamlEditor").getFont().getSize());
            String yaml = "test:\n  name: Test\n  bpm: 125\n  tracks:\n    dundun: 'x.......'\n";
            var loaded = new RhythmPlayerPanel(yaml, config);
            assertEquals(125, ((JSlider) find(loaded, "tempoSlider")).getValue());
            assertEquals(125, ((JSlider) find(loaded, "tempoSlider")).getMaximum());
            assertEquals(37, ((JSlider) find(loaded, "humanizeSlider")).getValue());
        });
    }

    private Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) {
                Component found = find(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
