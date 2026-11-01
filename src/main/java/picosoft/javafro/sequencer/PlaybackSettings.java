package picosoft.javafro.sequencer;

/** Thread-safe live controls, shared by the UI and the audio thread. */
public final class PlaybackSettings {
    public record Values(int bpm, int humanize) { }

    private volatile Values values;

    public PlaybackSettings(int bpm, int humanize) {
        validate(bpm, humanize);
        values = new Values(bpm, humanize);
    }

    public Values snapshot() {
        return values;
    }

    public synchronized void setBpm(int bpm) {
        validate(bpm, values.humanize());
        values = new Values(bpm, values.humanize());
    }

    public synchronized void setHumanize(int humanize) {
        validate(values.bpm(), humanize);
        values = new Values(values.bpm(), humanize);
    }

    private static void validate(int bpm, int humanize) {
        if (bpm <= 0) throw new IllegalArgumentException("BPM must be greater than zero.");
        if (humanize < 0 || humanize > 100) throw new IllegalArgumentException("Humanize must be between 0 and 100.");
    }
}
