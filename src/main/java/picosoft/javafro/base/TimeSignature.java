package picosoft.javafro.base;

/** Defaults to eighth-note steps; compound eighth meters use dotted-quarter BPM. */
public record TimeSignature(int numerator, int denominator) {
    public TimeSignature {
        if (numerator <= 0 || numerator > Integer.MAX_VALUE / 2
                || (denominator != 4 && denominator != 8)) {
            throw new IllegalArgumentException("Time signature must have a positive numerator and denominator 4 or 8.");
        }
    }

    public static TimeSignature parse(String value) {
        if (value == null || !value.matches("[1-9][0-9]*/[48]")) {
            throw new IllegalArgumentException("Time signature must be written as e.g. '4/4' or '12/8'.");
        }
        String[] parts = value.split("/");
        try {
            return new TimeSignature(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Time signature numerator is too large.", exception);
        }
    }

    public int stepsPerBar() {
        return denominator == 4 ? numerator * 2 : numerator;
    }

    public int stepsPerBar(int stepsPerBeat) {
        long steps = (long) stepsPerBar() * stepsPerBeat;
        if (stepsPerBeat <= 0 || stepsPerBeat > 10000 || steps % stepsPerBeat() != 0
                || steps / stepsPerBeat() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Steps per beat must be 1–10000 and produce a whole number of steps per bar.");
        }
        return (int) (steps / stepsPerBeat());
    }

    public int stepsPerBeat() {
        return denominator == 8 && numerator >= 6 && numerator % 3 == 0 ? 3 : 2;
    }

    public void validatePattern(String pattern) {
        validatePattern(pattern, stepsPerBeat());
    }

    public void validatePattern(String pattern, int stepsPerBeat) {
        int expected = stepsPerBar(stepsPerBeat);
        if (pattern == null || !pattern.matches("[a-zA-Z.]+(\\|[a-zA-Z.]+)*")) {
            throw new IllegalArgumentException("Pattern must contain non-empty bars of note letters and '.', separated by '|'.");
        }
        for (String bar : pattern.split("\\|", -1)) {
            if (bar.length() != expected) {
                throw new IllegalArgumentException("Each bar in " + this + " must contain " + expected
                        + " steps; found " + bar.length() + ". Separate bars with '|'.");
            }
        }
    }

    @Override
    public String toString() {
        return numerator + "/" + denominator;
    }
}
