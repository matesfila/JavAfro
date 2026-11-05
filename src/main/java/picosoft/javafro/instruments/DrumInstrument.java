package picosoft.javafro.instruments;

import picosoft.javafro.base.Instrument;

/** Track-specific notation for single-sample and synthetic drums; x selects only the bell. */
public abstract class DrumInstrument implements Instrument {
    private final Instrument drum;
    private final Instrument bell;
    private final char stroke;

    protected DrumInstrument(Instrument drum, Instrument bell, char stroke) {
        this.drum = drum;
        this.bell = bell;
        this.stroke = Character.toUpperCase(stroke);
    }

    @Override public final boolean supportsNote(char note) {
        return note == 'x' || note == stroke || note == Character.toLowerCase(stroke);
    }

    @Override public final Instrument voice(char note, int humanize) {
        if (!supportsNote(note)) throw new IllegalArgumentException("Unsupported drum note: " + note);
        if (humanize < 0 || humanize > 100) throw new IllegalArgumentException("Humanize must be 0–100.");
        return note == 'x' ? bell.voice('x', humanize) : drum.voice('x', humanize);
    }

    @Override public final double sample(long index) { return drum.sample(index); }
    @Override public final long lengthSamples(long pulse) { return drum.lengthSamples(pulse); }
    @Override public final double envelope(long index, long length) { return drum.envelope(index, length); }
}
