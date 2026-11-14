package picosoft.javafro.base;

import java.util.List;

public interface Track {

    static boolean isValidPattern(String pattern) {
        if (pattern == null) return false;
        boolean hasNote = false;
        for (int i = 0; i < pattern.length(); i++) {
            char note = pattern.charAt(i);
            if (note == '|') {
                if (!hasNote) return false;
                hasNote = false;
            } else {
                if (!(note == '.' || (note >= 'a' && note <= 'z') || (note >= 'A' && note <= 'Z'))) return false;
                hasNote = true;
            }
        }
        return hasNote;
    }

    String pattern();

    Instrument instrument();

    /** Optional alternative patterns; normal playback uses pattern(). */
    default List<String> variations() {
        return List.of();
    }

}
