package picosoft.javafro.sequencer;

import picosoft.javafro.base.Instrument;
import picosoft.javafro.base.Track;

public record BeepTrack(String pattern, Instrument instrument) implements Track {
    public BeepTrack {
        if (pattern == null || !pattern.matches("[x.]+")) {
            throw new IllegalArgumentException("Pattern must contain only 'x' and '.' and must not be empty.");
        }
        if (instrument == null) {
            throw new IllegalArgumentException("Track instrument must not be null.");
        }
    }
}
