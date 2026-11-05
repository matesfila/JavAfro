package picosoft.javafro.instruments.sample.jimmijames;

import picosoft.javafro.instruments.DrumInstrument;
import picosoft.javafro.instruments.sample.WavSampleInstrument;
import picosoft.javafro.instruments.sample.matesamples.DundunBellInstrument;

/** Recorded dundunba replica, JIMMYJAMES112 / Freesound, CC0. */
public final class DundunInstrument extends DrumInstrument {
    public DundunInstrument() {
        super(new WavSampleInstrument("/samples/jimmyjames/dundun.wav") {}, new DundunBellInstrument(), 'D');
    }
}
