package picosoft.javafro.base;

import java.util.List;
import java.util.Objects;

/** A named rhythm with its tracks and default tempo; playback settings are separate. */
public record Rhythm(String name, List<? extends Track> tracks, int bpm, TimeSignature timeSignature, int stepsPerBeat) {
    public Rhythm(String name, List<? extends Track> tracks, int bpm) {
        this(name, tracks, bpm, new TimeSignature(4, 4));
    }

    public Rhythm(String name, List<? extends Track> tracks, int bpm, TimeSignature timeSignature) {
        this(name, tracks, bpm, timeSignature, timeSignature == null ? 0 : timeSignature.stepsPerBeat());
    }

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
        if (timeSignature == null) {
            throw new IllegalArgumentException("Time signature must not be null.");
        }
        for (Track track : tracks) {
            timeSignature.validatePattern(track.pattern(), stepsPerBeat);
            for (String variation : track.variations()) {
                timeSignature.validatePattern(variation, stepsPerBeat);
            }
        }
        tracks = List.copyOf(tracks);
    }
}
