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
    public void everyYamlLoadsItsNamedDefinitionsWithValidDialect() throws IOException {
        try (var paths = Files.walk(Path.of("rhythms"))) {
            var files = paths.filter(p -> p.toString().endsWith(".yaml")).toList();
            assertFalse(files.isEmpty());
            for (Path file : files) {
                Map<?, ?> document = new Yaml().load(Files.readString(file));
                var rhythms = RhythmLoader.load(file);
                assertEquals(file.toString(), document.keySet(), rhythms.keySet());
                for (var entry : rhythms.entrySet()) {
                    Map<?, ?> definition = (Map<?, ?>) document.get(entry.getKey());
                    assertFalse(file.toString(), definition.containsKey("swing"));
                    Rhythm rhythm = entry.getValue();
                    assertEquals(entry.getKey(), rhythm.name());
                    assertEquals(rhythm.stepsPerBeat(), rhythm.dialect().offsets().size());
                }
            }
        }
    }

    @Test
    public void allImportedRhythmsLoadAndPreserveDrumHitTimes() throws Exception {
        int count = 0;
        try (var paths = Files.list(Path.of("rhythms/imported"))) {
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
                    int groupedBars = java.util.Set.of("Djole", "Kuku", "Fakoli", "Jamakale", "_Test-rhythm", "Gambia")
                            .contains(entry.getKey()) ? 2 : 1;
                    assertEquals(originalBarBeats * groupedBars, newBarBeats, 1e-12);
                    Map<?, ?> originals = (Map<?, ?>) original.get("patterns");
                    for (String id : List.of("dundun", "sangban", "kenken")) {
                        if (!originals.containsKey(id)) continue;
                        Map<?, ?> oldTrack = (Map<?, ?>) originals.get(id);
                        List<?> base = (List<?>) oldTrack.get("base");
                        if (base == null || base.isEmpty()) {
                            base = (List<?>) oldTrack.get("echauff");
                            if (base == null || base.isEmpty()) base = List.of("");
                        }
                        String instrumentClass = switch (id) {
                            case "dundun" -> "DundunInstrument";
                            case "sangban" -> "SangbanInstrument";
                            default -> "KensereniInstrument";
                        };
                        Track track = rhythm.tracks().stream()
                                .filter(t -> t.instrument().getClass().getSimpleName().equals(instrumentClass))
                                .findFirst().orElseThrow();
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
                    sequencer.playRhythm(rhythm, 1, 0); // Real playback validation, no audio device.
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
    public void gambiaDjembePreservesRubyNotesAndRoundsSoloTimingToFourSteps() throws IOException {
        Path file = Path.of("rhythms/imported/Gambia.yaml");
        Rhythm rhythm = RhythmLoader.load(file).get("Gambia");
        assertEquals("4/4", rhythm.timeSignature().toString());
        assertEquals(240, rhythm.bpm());
        assertEquals(4, rhythm.stepsPerBeat());
        Track djembe = rhythm.tracks().stream()
                .filter(t -> t.instrument() instanceof picosoft.javafro.instruments.sample.DjembeInstrument)
                .findFirst().orElseThrow();
        Map<?, ?> document = new Yaml().load(Files.readString(file));
        Map<?, ?> definition = (Map<?, ?>) document.get("Gambia");
        Map<?, ?> legacy = (Map<?, ?>) definition.get("legacy");
        Map<?, ?> original = (Map<?, ?>) legacy.get("definition");
        Map<?, ?> patterns = (Map<?, ?>) original.get("patterns");
        Map<?, ?> source = (Map<?, ?>) patterns.get("djembe");
        var originals = new ArrayList<String>();
        originals.add((String) ((List<?>) source.get("base")).getFirst());
        for (Object variation : (List<?>) source.get("variations")) originals.add((String) variation);
        var converted = new ArrayList<String>();
        converted.add(djembe.pattern());
        converted.addAll(djembe.variations());
        assertEquals(5, converted.size());
        for (int p = 0; p < originals.size(); p++) {
            String[] sections = originals.get(p).split("\\|");
            var expectedTimes = new ArrayList<Double>();
            var expectedNotes = new StringBuilder();
            for (int section = 0; section < sections.length; section++) {
                String bar = sections[section];
                for (int i = 0; i < bar.length(); i++) if (bar.charAt(i) != '.') {
                    expectedNotes.append(bar.charAt(i));
                    double originalTime = section * 2.0 + i * 2.0 / bar.length();
                    double quantizedTime = section * 2.0 + Math.round(i * 8.0 / bar.length()) / 4.0;
                    assertTrue(Math.abs(originalTime - quantizedTime) <= 0.125);
                    if (p == 0) assertEquals(originalTime, quantizedTime, 1e-10);
                    expectedTimes.add(quantizedTime);
                }
            }
            String steps = converted.get(p).replace("|", "");
            assertEquals(sections.length * 2.0, steps.length() / 4.0, 1e-10);
            var actualNotes = new StringBuilder();
            int note = 0;
            for (int i = 0; i < steps.length(); i++) if (steps.charAt(i) != '.') {
                actualNotes.append(steps.charAt(i));
                assertEquals(expectedTimes.get(note++), i / 4.0, 1e-10);
            }
            assertEquals(expectedNotes.toString(), actualNotes.toString());
        }
    }

    @Test
    public void remainingFourFourConversionsPreserveArchivedDrumHitTimes() throws IOException {
        for (String id : List.of("Fakoli", "Jamakale", "_Test-rhythm")) {
            Path file = Path.of("rhythms/imported", id + ".yaml");
            Rhythm rhythm = RhythmLoader.load(file).get(id);
            assertEquals("4/4", rhythm.timeSignature().toString());
            assertEquals(4, rhythm.stepsPerBeat());
            Map<?, ?> document = new Yaml().load(Files.readString(file));
            Map<?, ?> definition = (Map<?, ?>) document.get(id);
            Map<?, ?> legacy = (Map<?, ?>) definition.get("legacy");
            Map<?, ?> original = (Map<?, ?>) legacy.get("definition");
            Map<?, ?> patterns = (Map<?, ?>) original.get("patterns");
            assertEquals(original.get("BPM"), rhythm.bpm());
            for (Track track : rhythm.tracks()) {
                String instrument = track.instrument().getClass().getSimpleName();
                String sourceId = switch (instrument) {
                    case "DundunInstrument" -> "dundun";
                    case "SangbanInstrument" -> "sangban";
                    case "KensereniInstrument" -> "kenken";
                    default -> null; // Djembe may have been edited after import.
                };
                if (sourceId == null) continue;
                Map<?, ?> source = (Map<?, ?>) patterns.get(sourceId);
                checkHitTimes((List<?>) source.get("base"), track.pattern(), 4, 2);
                List<?> variations = source.get("variations") instanceof List<?> list ? list : List.of();
                assertEquals(variations.size(), track.variations().size());
                for (int i = 0; i < variations.size(); i++) {
                    checkHitTimes(List.of(variations.get(i)), track.variations().get(i), 4, 2);
                }
            }
        }
    }

    @Test
    public void djoleAndKukuRetainVariationsAndBaseSequences() throws IOException {
        var djole = RhythmLoader.load(Path.of("rhythms/imported/Djole.yaml")).get("Djole");
        assertEquals("4/4", djole.timeSignature().toString());
        var track = (BeepTrack) djole.tracks().stream()
                .filter(t -> t.instrument() instanceof picosoft.javafro.instruments.sample.DundunInstrument)
                .findFirst().orElseThrow();
        assertEquals("x.......x.x.....", track.pattern());
        assertEquals("xx.xxx.xxx.xx.x.", track.withVariation(0).pattern());
        assertThrows(UnsupportedOperationException.class, () -> track.variations().clear());
        var kuku = RhythmLoader.load(Path.of("rhythms/imported/Kuku.yaml")).get("Kuku");
        assertEquals("4/4", kuku.timeSignature().toString());
        var kukuDundun = kuku.tracks().stream()
                .filter(t -> t.instrument() instanceof picosoft.javafro.instruments.sample.DundunInstrument)
                .findFirst().orElseThrow();
        assertEquals(13, kukuDundun.pattern().split("\\|").length);
        assertEquals(24, kukuDundun.variations().size());
        var kukuDjembe = kuku.tracks().stream()
                .filter(t -> t.instrument() instanceof picosoft.javafro.instruments.sample.DjembeInstrument)
                .findFirst().orElseThrow();
        assertEquals("s.ts..s.ts..".repeat(4), kukuDjembe.pattern().replace("|", ""));
    }
}
