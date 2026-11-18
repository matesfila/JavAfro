package picosoft.javafro.base;

import java.util.List;

public interface Track {

    static boolean isValidPattern(String pattern) {
        try { NotePattern.bars(pattern); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }

    String pattern();

    Instrument instrument();

    /** Exact YAML key when loaded from a song. */
    default String instrumentId() { return instrument().getClass().getSimpleName().replace("Instrument", ""); }

    /** Optional alternative patterns; normal playback uses pattern(). */
    default List<String> variations() {
        return List.of();
    }

}
