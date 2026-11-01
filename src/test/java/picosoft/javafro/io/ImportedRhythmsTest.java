package picosoft.javafro.io;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import picosoft.javafro.base.Rhythm;
import picosoft.javafro.base.Track;
import picosoft.javafro.sequencer.BeepSequencer;
import picosoft.javafro.sequencer.BeepTrack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ImportedRhythmsTest {
    @Test
    public void everyYamlHasOneRhythmNamedAfterItsFile() throws IOException {
        try (var paths = Files.walk(Path.of("src/main/resources/rhythms"))) {
            var files = paths.filter(p -> p.toString().endsWith(".yaml")).toList();
            assertEquals(25, files.size());
            for (Path file : files) {
                String id = file.getFileName().toString().replaceFirst("\\.yaml$", "");
                assertEquals(file.toString(), java.util.Set.of(id), RhythmLoader.load(file).keySet());
            }
        }
    }

    @Test
    public void allImportedRhythmsLoadAndPreserveDrumHitTimes() throws Exception {
        int count = 0;
        try (var paths = Files.list(Path.of("src/main/resources/rhythms/imported"))) {
            var files = paths.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
            assertEquals(23, files.size());
            for (Path file : files) {
                Map<String, Rhythm> loaded = RhythmLoader.load(file);
                assertEquals(java.util.Set.of(file.getFileName().toString().replaceFirst("\\.yaml$", "")), loaded.keySet());
                Map<?, ?> document;
                try (var input = Files.newInputStream(file)) {
                    document = new Yaml().load(input);
                }
                for (var entry : loaded.entrySet()) {
                    count++;
                    Rhythm rhythm = entry.getValue();
                    Map<?, ?> definition = (Map<?, ?>) document.get(entry.getKey());
                    Map<?, ?> archive = (Map<?, ?>) definition.get("legacy");
                    Map<?, ?> original = (Map<?, ?>) archive.get("definition");
                    assertEquals(original.get("BPM"), rhythm.bpm());
                    // Source snapshots are optional; the YAML archive is self-contained.
                    assertTrue(((String) archive.get("source")).endsWith(".rb"));
                    assertNotNull(original.get("SWING"));
                    List<?> signature = (List<?>) original.get("TIME_SIGNATURE");
                    int numerator = (Integer) signature.getFirst();
                    double originalBarBeats = numerator / (numerator == 6 || numerator == 12 ? 3.0 : 4.0);
                    double newBarBeats = (double) rhythm.timeSignature().stepsPerBar(rhythm.stepsPerBeat()) / rhythm.stepsPerBeat();
                    assertEquals(originalBarBeats, newBarBeats, 1e-12);
                    Map<?, ?> originals = (Map<?, ?>) original.get("patterns");
                    int trackIndex = 0;
                    for (String id : List.of("dundun", "sangban", "kenken")) {
                        if (!originals.containsKey(id)) continue;
                        Map<?, ?> oldTrack = (Map<?, ?>) originals.get(id);
                        List<?> base = (List<?>) oldTrack.get("base");
                        if (base == null || base.isEmpty()) {
                            base = (List<?>) oldTrack.get("echauff");
                            if (base == null || base.isEmpty()) base = List.of("");
                        }
                        Track track = rhythm.tracks().get(trackIndex++);
                        checkHitTimes(base, track.pattern(), rhythm.stepsPerBeat(), originalBarBeats);
                        List<?> variations = (List<?>) oldTrack.get("variations");
                        if (variations == null) variations = List.of();
                        assertEquals(variations.size(), track.variations().size());
                        for (int i = 0; i < variations.size(); i++) {
                            checkHitTimes(List.of(variations.get(i)), track.variations().get(i), rhythm.stepsPerBeat(), originalBarBeats);
                        }
                    }
                    var sequencer = new BeepSequencer();
                    sequencer.stop();
                    sequencer.playPattern(rhythm, 1, 0); // Real playback validation, no audio device.
                }
            }
        }
        assertEquals(23, count);
    }

    private void checkHitTimes(List<?> originals, String converted, int stepsPerBeat, double barBeats) {
        List<Double> expected = new ArrayList<>();
        int barIndex = 0;
        for (Object original : originals) {
            String[] bars = ((String) original).isEmpty() ? new String[]{""} : ((String) original).split("\\|");
            for (String bar : bars) {
                for (int i = 0; i < bar.length(); i++) {
                    if ("XxIiABC".indexOf(bar.charAt(i)) >= 0) expected.add((barIndex + (double) i / bar.length()) * barBeats);
                }
                barIndex++;
            }
        }
        String steps = converted.replace("|", "");
        assertEquals(barIndex * barBeats, (double) steps.length() / stepsPerBeat, 1e-10);
        List<Double> actual = new ArrayList<>();
        for (int i = 0; i < steps.length(); i++) if (steps.charAt(i) == 'x') actual.add((double) i / stepsPerBeat);
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) assertEquals(expected.get(i), actual.get(i), 1e-10);
    }

    @Test
    public void djoleAndKukuRetainVariationsAndBaseSequences() throws IOException {
        var djole = RhythmLoader.load(Path.of("src/main/resources/rhythms/imported/Djole.yaml")).get("Djole");
        assertEquals("x.......|x.x.....", djole.tracks().getFirst().pattern());
        var track = (BeepTrack) djole.tracks().getFirst();
        assertEquals("xx.xxx.x|xx.xx.x.", track.withVariation(0).pattern());
        assertThrows(UnsupportedOperationException.class, () -> track.variations().clear());
        var kuku = RhythmLoader.load(Path.of("src/main/resources/rhythms/imported/Kuku.yaml")).get("Kuku");
        assertEquals(26, kuku.tracks().getFirst().pattern().split("\\|").length);
        assertEquals(24, kuku.tracks().getFirst().variations().size());
    }
}
