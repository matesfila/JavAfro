package picosoft.javafro.base;

import java.util.List;

public interface Track {

    String pattern();

    Instrument instrument();

    /** Optional alternative patterns; normal playback uses pattern(). */
    default List<String> variations() {
        return List.of();
    }

}
