package picosoft.javafro.base;

import java.util.Arrays;
import java.util.List;

/** Space-delimited patterns use one token per pulse; compact patterns use one character. */
public final class NotePattern {
    private NotePattern() {}

    public static boolean isSpaced(String pattern) { return pattern.matches("(?s).*\\s.*"); }

    public static List<List<String>> bars(String pattern) {
        if (pattern == null || pattern.isBlank()) throw new IllegalArgumentException("Pattern must not be empty.");
        boolean spaced = isSpaced(pattern);
        return Arrays.stream(pattern.split("\\|", -1)).map(bar -> {
            String trimmed = bar.trim();
            if (trimmed.isEmpty()) throw new IllegalArgumentException("Pattern contains an empty bar.");
            List<String> notes = spaced ? Arrays.stream(trimmed.split("\\s+")).map(note -> note.equals("..") ? "." : note).toList()
                    : trimmed.chars().mapToObj(c -> String.valueOf((char) c)).toList();
            for (String note : notes) {
                if (!note.matches("[a-zA-Z][a-zA-Z0-9]*|\\.")) {
                    throw new IllegalArgumentException("Invalid note token: " + note);
                }
            }
            return notes;
        }).toList();
    }

    public static List<String> notes(String pattern) { return bars(pattern).stream().flatMap(List::stream).toList(); }
}
