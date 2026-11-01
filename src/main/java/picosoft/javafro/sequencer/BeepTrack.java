package picosoft.javafro.sequencer;

import picosoft.javafro.base.Instrument;
import picosoft.javafro.base.Track;

import java.util.List;

public record BeepTrack(String pattern, Instrument instrument, List<String> variations) implements Track {
    public BeepTrack(String pattern, Instrument instrument) {
        this(pattern, instrument, List.of());
    }

    public BeepTrack {
        if (pattern == null || !pattern.matches("[x.]+(\\|[x.]+)*")) {
            throw new IllegalArgumentException("Pattern must contain non-empty bars of 'x' and '.', separated by '|'.");
        }
        if (instrument == null) {
            throw new IllegalArgumentException("Track instrument must not be null.");
        }
        if (variations == null || variations.stream().anyMatch(value -> value == null
                || !value.matches("[x.]+(\\|[x.]+)*"))) {
            throw new IllegalArgumentException("Variations must be non-null valid patterns.");
        }
        variations = List.copyOf(variations);
    }

    /** Explicit selection; the sequencer never chooses variations automatically. */
    public BeepTrack withVariation(int index) {
        return new BeepTrack(variations.get(index), instrument, variations);
    }
}
