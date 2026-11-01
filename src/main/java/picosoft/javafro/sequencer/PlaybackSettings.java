package picosoft.javafro.sequencer;

import picosoft.javafro.base.Swing;

/** Thread-safe live controls, shared by the UI and the audio thread. */
public final class PlaybackSettings {
    public static final int DEFAULT_HUMANIZE = 21;

    public record Values(int bpm, int humanize, Swing swing) {
        public Values(int bpm, int humanize) { this(bpm, humanize, null); }
    }

    private volatile Values values;

    public PlaybackSettings(int bpm, int humanize) {
        this(bpm, humanize, null);
    }

    public PlaybackSettings(int bpm, int humanize, Swing swing) {
        validate(bpm, humanize);
        values = new Values(bpm, humanize, swing);
    }

    public Values snapshot() {
        return values;
    }

    public synchronized void setBpm(int bpm) {
        validate(bpm, values.humanize());
        values = new Values(bpm, values.humanize(), values.swing());
    }

    public synchronized void setHumanize(int humanize) {
        validate(values.bpm(), humanize);
        values = new Values(values.bpm(), humanize, values.swing());
    }

    /** Pulse offsets are applied together at the next beat boundary. */
    public synchronized void setSwing(Swing swing) {
        if (swing == null || (values.swing() != null && swing.offsets().size() != values.swing().offsets().size())) {
            throw new IllegalArgumentException("Live swing must keep the same number of steps per beat.");
        }
        values = new Values(values.bpm(), values.humanize(), swing);
    }

    private static void validate(int bpm, int humanize) {
        if (bpm <= 0) throw new IllegalArgumentException("BPM must be greater than zero.");
        if (humanize < 0 || humanize > 100) throw new IllegalArgumentException("Humanize must be between 0 and 100.");
    }
}
