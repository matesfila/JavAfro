package picosoft.javafro.instruments.sample.jimmijames;

import picosoft.javafro.instruments.DrumInstrument;
import picosoft.javafro.instruments.sample.WavSampleInstrument;
import picosoft.javafro.instruments.sample.matesamples.KensereniBellInstrument;

/** Recorded kenkeni replica, JIMMYJAMES112 / Freesound, CC0. */
public final class KensereniInstrument extends DrumInstrument {
    public KensereniInstrument() {
        super(new WavSampleInstrument("/samples/jimmyjames/kenkeni.wav") {}, new KensereniBellInstrument(), 'K');
    }
}
