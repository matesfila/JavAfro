package picosoft.javafro.sequencer;

import picosoft.javafro.base.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Prehráva rytmické patterny viacerých trackov súčasne v zadanom tempe.
 * Každý znak patternu predstavuje jednu šestnástinu: {@code x} spustí úder
 * priradeného {@link Instrument}u a {@code .} predstavuje ticho. BPM určuje počet
 * štvrťových dôb za minútu, takže jedna pozícia trvá {@code 60 / (4 * BPM)} sekundy.
 *
 * <p>Dĺžku jedného opakovania určuje najdlhší pattern. Kratšie patterny
 * sa počas neho cyklia a pri ďalšom opakovaní začnú všetky tracky od začiatku.
 * Sekvencer pozná nástroje iba cez rozhranie {@link Instrument}, takže možno použiť
 * syntetický tón, WAV sample aj vlastnú implementáciu.</p>
 *
 * <h2>Zvukový výstup cez Java Sound</h2>
 * <p>{@link AudioConstants#audioFormat()} vytvorí {@link AudioFormat}:
 * lineárne PCM so znamienkom, 44 100 vzoriek za sekundu, 16 bitov na vzorku,
 * jeden kanál (mono) a poradie bajtov little-endian. Jedna vzorka teda zaberá
 * dva bajty. {@link AudioSystem#getSourceDataLine(AudioFormat)} vyhľadá
 * kompatibilnú {@link SourceDataLine} v predvolenom výstupnom mixéri.
 * Volanie {@link SourceDataLine#open(AudioFormat)} pripraví linku a jej
 * interný buffer; {@link SourceDataLine#start()} spustí odoberanie vzoriek
 * zvukovým systémom. Nejde o MIDI ani o ovládanie hardvérového PC speakra.</p>
 *
 * <h2>Prevod tempa na vzorky</h2>
 * <p>Pri vzorkovacej frekvencii {@code Fs = 44100} pripadá na šestnástinu
 * {@code Fs * 60 / (4 * BPM)} vzoriek. Napríklad pri 120 BPM je to
 * 5 512,5 vzorky. Keďže do výstupu možno zapísať iba celý počet vzoriek,
 * premenná {@code remainder} prenáša zvyšok delenia do ďalšieho pulzu:
 * v tomto príklade sa striedajú dĺžky 5 512 a 5 513 vzoriek.
 * Zvyšok sa zachováva aj medzi opakovaniami patternu.</p>
 *
 * <h2>Hlasy, syntéza a miešanie</h2>
 * <p>Na začiatku každého pulzu sa pre každý track s {@code x} na pozícii
 * {@code pulse % pattern.length()} vytvorí „hlas“: nástroj, globálny index
 * vzorky začiatku úderu a dĺžka {@link Instrument#lengthSamples(long)}.
 * Hlas znie, kým neuplynie jeho dĺžka, aj keď presiahne hranicu pulzu.
 * Preto môže dlhý sample doznieť a prekrývať sa s ďalšími údermi
 * (aj toho istého tracku).</p>
 *
 * <p>Pre každú výstupnú vzorku sa sčítajú všetky aktívne hlasy:
 * {@code instrument.sample(i) * instrument.envelope(i, length)}, kde {@code i} je
 * počet vzoriek od začiatku úderu. Súčet sa delí počtom všetkých trackov
 * a {@link AudioConstants#putSample(byte[], int, double)} ho zoslabí na 25 %,
 * orežie na [-1, 1], prevedie na 16-bitové celé číslo a uloží jeho dva bajty
 * do opakovane používaného poľa {@code byte[]}. Súčasné prehrávanie teda
 * vzniká miešaním vzoriek, nie samostatným vláknom pre každý track.</p>
 *
 * <h2>Bufferovanie a životný cyklus</h2>
 * <p>Sekvencer vytvára bloky najviac 4 096 vzoriek (8 192 bajtov).
 * {@link SourceDataLine#write(byte[], int, int)} ich kopíruje do interného
 * buffera linky a pri nedostatku miesta čaká, kým sa časť zvuku prehrá.
 * Tempo tak určuje počet vzoriek a ich odoberanie zvukovým zariadením,
 * nie {@link Thread#sleep(long)}. Táto synchronizácia trackov je presná
 * na úrovni vzoriek; výstup však môže mať latenciu danú bufferovaním
 * a pri oneskorenom dodávaní dát môže vzniknúť výpadok zvuku.</p>
 *
 * <p>Po poslednom pulze sa dorenderuje doznievanie aktívnych hlasov a až potom
 * {@link SourceDataLine#drain()} počká na prehratie čakajúcich dát.
 * Konštrukcia {@code try-with-resources} následne linku uzavrie aj pri chybe.
 * Nedostupná zvuková linka sa hlási výnimkou {@link LineUnavailableException};
 * nepodporovaný formát môže viesť k {@link IllegalArgumentException}. Celý priebeh
 * generovania a zápisu vykonáva volajúce vlákno.</p>
 */
public final class BeepSequencer implements Sequencer {

    private record Voice(Instrument instrument, long start, long length) {
        long end() {
            return start + length;
        }
    }

    @Override
    public void playPattern(String pattern, int repetitions, int bpm) throws LineUnavailableException {
        playPattern(List.of(new BeepTrack(pattern, new BeepInstrument())), repetitions, bpm);
    }

    @Override
    public void playPattern(List<? extends Track> tracks, int repetitions, int bpm) throws LineUnavailableException {
        if (tracks == null || tracks.isEmpty() || tracks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("At least one non-null track is required; tracks must not contain null.");
        }
        List<BeepTrack> validatedTracks = tracks.stream()
                .map(track -> new BeepTrack(track.pattern(), track.instrument()))
                .toList();
        if (repetitions <= 0 || bpm <= 0) {
            throw new IllegalArgumentException("Repetitions and BPM must be greater than zero.");
        }
        long sixteenthNotesPerMinute = bpm * 4L;
        if (AudioConstants.SAMPLE_RATE * 60L / sixteenthNotesPerMinute < 4) {
            throw new IllegalArgumentException("BPM is too high to render an audible pulse at the current sample rate.");
        }
        int patternLength = validatedTracks.stream().mapToInt(track -> track.pattern().length()).max().orElseThrow();

        AudioFormat format = AudioConstants.audioFormat();
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();

            byte[] buffer = new byte[AudioConstants.SAMPLES_PER_BUFFER * 2];
            List<Voice> voices = new ArrayList<>();
            long position = 0;
            long remainder = 0;
            for (int repeat = 0; repeat < repetitions; repeat++) {
                for (int pulse = 0; pulse < patternLength; pulse++) {
                    // Carry fractional samples forward so rounding does not accumulate tempo drift.
                    long numerator = AudioConstants.SAMPLE_RATE * 60L + remainder;
                    long pulseSamples = numerator / sixteenthNotesPerMinute;
                    remainder = numerator % sixteenthNotesPerMinute;

                    for (BeepTrack track : validatedTracks) {
                        if (track.pattern().charAt(pulse % track.pattern().length()) == 'x') {
                            long length = track.instrument().lengthSamples(pulseSamples);
                            if (length > 0) {
                                voices.add(new Voice(track.instrument(), position, length));
                            }
                        }
                    }
                    position = render(line, buffer, voices, position, pulseSamples, validatedTracks.size());
                }
            }

            long tailEnd = voices.stream().mapToLong(Voice::end).max().orElse(position);
            render(line, buffer, voices, position, tailEnd - position, validatedTracks.size());
            line.drain();
        }
    }

    /** Vyrenderuje {@code count} vzoriek od pozície {@code from}, zapíše ich do linky a vráti novú pozíciu. */
    private static long render(SourceDataLine line, byte[] buffer, List<Voice> voices, long from, long count, int trackCount) {
        long position = from;
        long end = from + count;
        while (position < end) {
            int block = (int) Math.min(AudioConstants.SAMPLES_PER_BUFFER, end - position);
            for (int i = 0; i < block; i++, position++) {
                double mixed = 0;
                for (Voice voice : voices) {
                    long index = position - voice.start();
                    if (index >= 0 && index < voice.length()) {
                        mixed += voice.instrument().sample(index) * voice.instrument().envelope(index, voice.length());
                    }
                }
                AudioConstants.putSample(buffer, i, mixed / trackCount);
            }
            line.write(buffer, 0, block * 2);
            long blockEnd = position;
            voices.removeIf(voice -> voice.end() <= blockEnd);
        }
        return position;
    }
}
