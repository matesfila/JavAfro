package picosoft.javafro.base;

import java.util.List;
import java.util.Objects;

/** A named rhythm with its tracks and default tempo; playback settings are separate. */
public record Rhythm(String name, List<? extends Track> tracks, int bpm) {
    public Rhythm {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Rhythm name must not be blank.");
        }
        if (tracks == null || tracks.isEmpty() || tracks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("At least one non-null track is required; tracks must not contain null.");
        }
        if (bpm <= 0) {
            throw new IllegalArgumentException("BPM must be greater than zero.");
        }
        tracks = List.copyOf(tracks);
    }
}
