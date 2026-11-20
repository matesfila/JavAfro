package picosoft.javafro.config;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.junit.Test;
import picosoft.javafro.ui.RhythmPlayerPanel;

import javax.swing.*;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.font.TextAttribute;
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
        assertEquals(40, config.defaultTrackVolume());
        assertEquals(25, config.defaultMasterVolume());
        assertEquals(60, parse("playback: {defaultMasterVolume: 60}").defaultMasterVolume());
        assertEquals(100, config.humanizeApplianceProbability());
        for (int probability : new int[]{0, 50, 100}) {
            assertEquals(probability, parse("playback: {humanizeApplianceProbability: " + probability + "}").humanizeApplianceProbability());
        }
        for (int volume : new int[]{0, 35, 65, 100}) {
            assertEquals(volume, parse("playback: {defaultTrackVolume: " + volume + "}").defaultTrackVolume());
        }
        assertEquals(30, config.humanizeMaxOffsetMs());
        assertEquals(.07, config.humanizeDynamic(), 0);
        assertEquals(.005, config.humanizePitch(), 0);
        var offsets = parse("playback: {humanizeDynamic: 0.2, humanizePitch: 0}");
        assertEquals(.2, offsets.humanizeDynamic(), 0);
        assertEquals(0, offsets.humanizePitch(), 0);
        assertEquals(0, parse("playback: {humanizeMaxOffsetMs: 0}").humanizeMaxOffsetMs());
        assertEquals(12, parse("playback: {humanizeMaxOffsetMs: 12}").humanizeMaxOffsetMs());
        assertEquals(116, config.initialTempo());
        assertEquals(20, config.minimumTempo());
        assertEquals(300, config.maximumTempo());
        assertEquals(Path.of("target", "config-test", "moje-rytmy").toAbsolutePath(), config.rhythmsDirectory());
        assertEquals(16, parse("").editorFontSize());
        assertEquals(new AppConfig.EditorStyle("Consolas", 2, 0x6B3A1E, true, 0.1), parse("").editorStyle());
    }

    @Test
    public void editorStyleIsConfigurable() {
        AppConfig config = parse("editor:\n  fontFamily: Courier New\n  lineHeight: 1.5\n"
                + "  strings: {color: '#a0b1C2', bold: false, letterSpacing: 0}\n");
        assertEquals(new AppConfig.EditorStyle("Courier New", 1.5, 0xA0B1C2, false, 0), config.editorStyle());
        assertEquals(new AppConfig.EditorStyle("Consolas", 2, 0x123456, true, 0.1),
                parse("editor: {strings: {color: '#123456'}}").editorStyle());
    }

    @Test
    public void rejectsInvalidEditorStyle() {
        for (String yaml : new String[]{"editor: {fontFamily: ''}", "editor: {fontFamily: 'Consolas; color: red'}",
                "editor: {fontFamily: '\"Consolas\"'}", "editor: {fontFamily: 12}", "editor: {lineHeight: 0.9}",
                "editor: {lineHeight: 3.1}", "editor: {lineHeight: '2'}", "editor: {strings: {color: 6B3A1E}}",
                "editor: {strings: {color: '#6B3A1'}}", "editor: {strings: {color: '#6B3A1EFF'}}",
                "editor:\n  strings:\n    color: #6B3A1E\n", "editor: {strings: {bold: 'true'}}",
                "editor: {strings: {bold: 1}}", "editor: {strings: {letterSpacing: -0.1}}",
                "editor: {strings: {letterSpacing: 0.6}}", "editor: {strings: {italic: true}}", "editor: {strings: null}"}) {
            assertThrows(yaml, IllegalArgumentException.class, () -> parse(yaml));
        }
    }

    @Test
    public void projectConfigurationLoads() throws Exception {
        AppConfig config = AppConfig.load(Path.of("javafro.yaml"));
        assertTrue(config.humanize() >= 0 && config.humanize() <= 100);
        assertTrue(config.rhythmsDirectory().isAbsolute());
    }

    @Test
    public void rejectsTyposDuplicateKeysWrongTypesAndInvalidRanges() {
        for (String yaml : new String[]{"playback: {humanizeApplianceProbability: -1}", "playback: {humanizeApplianceProbability: 101}",
                "playback: {humanizeApplianceProbability: 50.5}", "playback: {humanizeApplianceProbability: '50'}",
                "playback: {defaultTrackVolume: -1}", "playback: {defaultTrackVolume: 101}",
                "playback: {defaultTrackVolume: 80.5}", "playback: {defaultTrackVolume: '80'}",
                "unknown: 1", "playback: null", "playback: {humanize: 101}",
                "playback: {humanize: -1}", "playback: {humanize: '21'}", "playback: {humanize: 21.5}",
                "playback: {humanize: 1, humanize: 2}", "editor: {fontSize: 0}", "window: {width: 100}",
                "library: {directory: ''}", "playback: {tempo: {minimum: 200, maximum: 100}}",
                "playback: {tempo: {initial: 301}}", "playback: {humanizeMaxOffsetMs: -1}",
                "playback: {humanizeMaxOffsetMs: 1001}", "playback: {humanizeMaxOffsetMs: '30'}",
                "playback: {humanizeMaxOffsetMs: 1.5}", "playback: {humanizeDynamic: -0.1}",
                "playback: {humanizeDynamic: .nan}", "playback: {humanizeDynamic: 1.1}",
                "playback: {humanizePitch: '0.005'}", "playback: {humanizePitch: .inf}",
                "playback: {humanizePitch: 0.51}", "[]", "playback: ["}) {
            assertThrows(yaml, IllegalArgumentException.class, () -> parse(yaml));
        }
    }

    @Test
    public void playerUsesConfiguredControlsAndKeepsRhythmTempo() throws Exception {
        AppConfig config = parse("playback:\n  humanize: 37\n  humanizeApplianceProbability: 63\n  tempo: {initial: 90, minimum: 40, maximum: 100}\neditor: {fontSize: 19}");
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RhythmPlayerPanel("", config);
            assertEquals(37, ((JSlider) find(panel, "humanizeSlider")).getValue());
            assertEquals(63, ((JSlider) find(panel, "humanizeProbabilitySlider")).getValue());
            JSlider tempo = (JSlider) find(panel, "tempoSlider");
            assertEquals(40, tempo.getMinimum());
            assertEquals(100, tempo.getMaximum());
            assertEquals(90, tempo.getValue());
            var editor = (RSyntaxTextArea) find(panel, "yamlEditor");
            assertEquals(19, editor.getFont().getSize());
            assertEquals(38, editor.getLineHeight()); // default lineHeight 2 × font size 19
            var string = editor.getSyntaxScheme().getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE);
            assertEquals(new Color(0x6B3A1E), string.foreground);
            assertTrue(string.font.isBold());
            assertEquals(0.1f, string.font.getAttributes().get(TextAttribute.TRACKING));
            String yaml = "test:\n  bpm: 125\n  tracks:\n    dundun_withBell: 'D.......'\n";
            var loaded = new RhythmPlayerPanel(yaml, config);
            assertEquals(125, ((JSlider) find(loaded, "tempoSlider")).getValue());
            assertEquals(125, ((JSlider) find(loaded, "tempoSlider")).getMaximum());
            assertEquals(37, ((JSlider) find(loaded, "humanizeSlider")).getValue());
            var withoutTempo = new RhythmPlayerPanel(yaml.replace("  bpm: 125\n", ""), config);
            assertEquals(90, ((JSlider) find(withoutTempo, "tempoSlider")).getValue());
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
