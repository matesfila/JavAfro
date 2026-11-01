package picosoft.javafro.sequencer;

import picosoft.javafro.base.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Prehráva rytmické patterny viacerých trackov súčasne v zadanom tempe.
 * Pri priamom prehrávaní patternu predstavuje každý znak jednu šestnástinu: {@code x} spustí úder
 * priradeného {@link Instrument}u a {@code .} predstavuje ticho. BPM určuje počet
 * štvrťových dôb za minútu, takže jedna pozícia trvá {@code 60 / (4 * BPM)} sekundy.
 * Pri prehrávaní {@link Rhythm} určuje počet pozícií na beat jeho
 * {@link Rhythm#stepsPerBeat()} (predvolene osminy). Oddeľovače taktov
 * {@code |} nezaberajú čas.
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

    private volatile boolean stopped;
    private SourceDataLine activeLine;

    /** Stops this sequencer permanently and discards queued audio immediately. */
    public synchronized void stop() {
        stopped = true;
        if (activeLine != null) {
            activeLine.stop();
            activeLine.flush();
            activeLine.close();
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    private record Voice(Instrument instrument, long start, long length) {
        long end() {
            return start + length;
        }
    }

    @Override
    public void playPattern(Rhythm rhythm, int repetitions, int humanize) throws LineUnavailableException {
        if (rhythm == null) {
            throw new IllegalArgumentException("Rhythm must not be null.");
        }
        playPattern(rhythm.tracks(), repetitions, rhythm.bpm(), humanize, rhythm.stepsPerBeat());
    }

    @Override
    public void playPattern(String pattern, int repetitions, int bpm) throws LineUnavailableException {
        playPattern(pattern, repetitions, bpm, 0);
    }

    @Override
    public void playPattern(String pattern, int repetitions, int bpm, int humanize) throws LineUnavailableException {
        playPattern(List.of(new BeepTrack(pattern, new BeepInstrument())), repetitions, bpm, humanize);
    }

    @Override
    public void playPattern(List<? extends Track> tracks, int repetitions, int bpm) throws LineUnavailableException {
        playPattern(tracks, repetitions, bpm, 0);
    }

    /**
     * Randomizes each hit independently by up to ±30 ms at 100, capped at a
     * quarter of a pattern step. The range scales linearly from zero.
     * Tempo stays fixed; starts before the beginning are clamped to zero.
     */
    @Override
    public void playPattern(List<? extends Track> tracks, int repetitions, int bpm, int humanize) throws LineUnavailableException {
        playPattern(tracks, repetitions, bpm, humanize, 4);
    }

    private void playPattern(List<? extends Track> tracks, int repetitions, int bpm, int humanize,
                             int stepsPerBeat) throws LineUnavailableException {
        if (humanize < 0 || humanize > 100) {
            throw new IllegalArgumentException("Humanize must be between 0 and 100.");
        }
        if (tracks == null || tracks.isEmpty() || tracks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("At least one non-null track is required; tracks must not contain null.");
        }
        List<BeepTrack> validatedTracks = tracks.stream()
                .map(track -> new BeepTrack(track.pattern(), track.instrument()))
                .map(track -> new BeepTrack(track.pattern().replace("|", ""), track.instrument()))
                .toList();
        if (repetitions <= 0 || bpm <= 0) {
            throw new IllegalArgumentException("Repetitions and BPM must be greater than zero.");
        }
        long stepsPerMinute = bpm * (long) stepsPerBeat;
        if (AudioConstants.SAMPLE_RATE * 60L / stepsPerMinute < 4) {
            throw new IllegalArgumentException("BPM is too high to render an audible pulse at the current sample rate.");
        }
        int patternLength = validatedTracks.stream().mapToInt(track -> track.pattern().length()).max().orElseThrow();

        long maxOffset = Math.round(Math.min(AudioConstants.SAMPLE_RATE * 0.030,
                AudioConstants.SAMPLE_RATE * 60.0 / stepsPerMinute / 4) * humanize / 100.0);
        AudioFormat format = AudioConstants.audioFormat();
        if (stopped) {
            return;
        }
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            synchronized (this) {
                if (stopped) {
                    return;
                }
                activeLine = line;
                line.start();
            }

            byte[] buffer = new byte[AudioConstants.SAMPLES_PER_BUFFER * 2];
            List<Voice> voices = new ArrayList<>();
            long position = 0;
            long renderedPosition = 0;
            long remainder = 0;
            for (int repeat = 0; repeat < repetitions; repeat++) {
                for (int pulse = 0; pulse < patternLength; pulse++) {
                    if (stopped) {
                        return;
                    }
                    // Carry fractional samples forward so rounding does not accumulate tempo drift.
                    long numerator = AudioConstants.SAMPLE_RATE * 60L + remainder;
                    long pulseSamples = numerator / stepsPerMinute;
                    remainder = numerator % stepsPerMinute;

                    for (BeepTrack track : validatedTracks) {
                        if (track.pattern().charAt(pulse % track.pattern().length()) == 'x') {
                            long length = track.instrument().lengthSamples(pulseSamples);
                            if (length > 0) {
                                voices.add(new Voice(track.instrument(), humanizedStart(position, maxOffset), length));
                            }
                        }
                    }
                    position += pulseSamples;
                    // Leave room for early hits in the next pulse, including across repeats.
                    long renderUntil = Math.max(0, position - maxOffset);
                    renderedPosition = render(line, buffer, voices, renderedPosition,
                            renderUntil - renderedPosition, validatedTracks.size());
                }
            }

            long tailEnd = Math.max(position, voices.stream().mapToLong(Voice::end).max().orElse(position));
            render(line, buffer, voices, renderedPosition, tailEnd - renderedPosition, validatedTracks.size());
            if (!stopped) {
                line.drain();
            }
        } finally {
            synchronized (this) {
                activeLine = null;
            }
        }
    }

    /** Vyrenderuje {@code count} vzoriek od pozície {@code from}, zapíše ich do linky a vráti novú pozíciu. */
    private long render(SourceDataLine line, byte[] buffer, List<Voice> voices, long from, long count, int trackCount) {
        long position = from;
        long end = from + count;
        while (position < end) {
            if (stopped) {
                return position;
            }
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

    static long humanizedStart(long position, long maxOffset) {
        if (maxOffset == 0) {
            return position;
        }
        return Math.max(0, position + ThreadLocalRandom.current().nextLong(-maxOffset, maxOffset + 1));
    }
}
