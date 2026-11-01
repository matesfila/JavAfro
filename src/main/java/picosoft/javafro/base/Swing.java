package picosoft.javafro.base;

import java.util.Collections;
import java.util.List;

/** Independent pulse offsets: -1/+1 means half a straight step early/late. */
public record Swing(List<Double> offsets) {
    public Swing {
        if (offsets == null || offsets.isEmpty() || offsets.stream().anyMatch(value -> value == null
                || !Double.isFinite(value) || value < -1 || value > 1)) {
            throw new IllegalArgumentException("Swing must contain finite pulse offsets between -1 and 1.");
        }
        offsets = List.copyOf(offsets);
    }

    public static Swing straight(int stepsPerBeat) {
        if (stepsPerBeat <= 0 || stepsPerBeat > 10000) throw new IllegalArgumentException("Steps per beat must be 1–10000.");
        return new Swing(Collections.nCopies(stepsPerBeat, 0.0));
    }

    /** Smallest derived interval, including the interval into the next beat. */
    public double minimumGap() {
        double minimum = 1;
        for (int i = 0; i < offsets.size(); i++) {
            minimum = Math.min(minimum, 1 + (offsets.get((i + 1) % offsets.size()) - offsets.get(i)) / 2);
        }
        return minimum;
    }

    /** Exactly one independent offset per pulse. */
    public static Swing of(int stepsPerBeat, List<? extends Number> values) {
        if (values == null || values.size() != stepsPerBeat) {
            throw new IllegalArgumentException("Swing must contain exactly stepsPerBeat pulse offsets.");
        }
        List<Double> offsets = new java.util.ArrayList<>();
        for (Number value : values) {
            if (value == null) throw new IllegalArgumentException("Swing offsets must not be null.");
            offsets.add(value.doubleValue());
        }
        return new Swing(offsets);
    }
}
