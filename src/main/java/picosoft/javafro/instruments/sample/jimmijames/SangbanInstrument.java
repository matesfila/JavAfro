package picosoft.javafro.instruments.sample.jimmijames;

import picosoft.javafro.instruments.DrumInstrument;
import picosoft.javafro.instruments.sample.WavSampleInstrument;
import picosoft.javafro.instruments.sample.matesamples.SangbanBellInstrument;

/** Recorded wooden sangban, JIMMYJAMES112 / Freesound, CC0. */
public final class SangbanInstrument extends DrumInstrument {
    public SangbanInstrument() {
        super(new WavSampleInstrument("/samples/jimmyjames/sangban.wav") {}, new SangbanBellInstrument(), 'S');
    }
}
