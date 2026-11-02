package picosoft.javafro.sequencer;

import picosoft.javafro.base.Dialect;
import picosoft.javafro.base.AudioConstants;

/** Thread-safe live controls, shared by the UI and the audio thread. */
public final class PlaybackSettings {
    public record Values(int bpm, int humanize, Dialect dialect) {
        public Values(int bpm, int humanize) { this(bpm, humanize, null); }
    }

    private volatile Values values;
    private volatile int humanizeApplianceProbability = 100;

    public int humanizeApplianceProbability() { return humanizeApplianceProbability; }

    public void setHumanizeApplianceProbability(int probability) {
        if (probability < 0 || probability > 100) throw new IllegalArgumentException("Humanize appliance probability must be between 0 and 100.");
        humanizeApplianceProbability = probability;
    }
    private volatile int masterVolume = (int) (AudioConstants.OUTPUT_GAIN * 100);

    public int masterVolume() { return masterVolume; }

    public void setMasterVolume(int volume) {
        if (volume < 0 || volume > 100) throw new IllegalArgumentException("Master volume must be between 0 and 100.");
        masterVolume = volume;
    }
    public record TrackMix(int volume, boolean muted) {
        public TrackMix {
            if (volume < 0 || volume > 200) throw new IllegalArgumentException("Track volume must be between 0 and 200.");
        }
        public double gain() { return muted ? 0 : volume / 100.0; }
    }
    private final java.util.concurrent.ConcurrentHashMap<Integer, TrackMix> mixer = new java.util.concurrent.ConcurrentHashMap<>();

    public TrackMix trackMix(int track) { return mixer.getOrDefault(track, new TrackMix(100, false)); }

    public void setTrackMix(int track, int volume, boolean muted) {
        if (track < 0) throw new IllegalArgumentException("Track index must not be negative.");
        mixer.put(track, new TrackMix(volume, muted));
    }

    public PlaybackSettings(int bpm, int humanize) {
        this(bpm, humanize, null);
    }

    public PlaybackSettings(int bpm, int humanize, Dialect dialect) {
        validate(bpm, humanize);
        values = new Values(bpm, humanize, dialect);
    }

    public Values snapshot() {
        return values;
    }

    public synchronized void setBpm(int bpm) {
        validate(bpm, values.humanize());
        values = new Values(bpm, values.humanize(), values.dialect());
    }

    public synchronized void setHumanize(int humanize) {
        validate(values.bpm(), humanize);
        values = new Values(values.bpm(), humanize, values.dialect());
    }

    /** Pulse offsets are applied together at the next beat boundary. */
    public synchronized void setDialect(Dialect dialect) {
        if (dialect == null || (values.dialect() != null && dialect.offsets().size() != values.dialect().offsets().size())) {
            throw new IllegalArgumentException("Live dialect must keep the same number of steps per beat.");
        }
        values = new Values(values.bpm(), values.humanize(), dialect);
    }

    private static void validate(int bpm, int humanize) {
        if (bpm <= 0) throw new IllegalArgumentException("BPM must be greater than zero.");
        if (humanize < 0 || humanize > AudioConstants.HUMANIZE_PERCENT_MAX) throw new IllegalArgumentException("Humanize must be between 0 and 100.");
    }
}
