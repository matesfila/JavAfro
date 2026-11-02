package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import picosoft.javafro.base.Rhythm;
import picosoft.javafro.config.AppConfig;
import picosoft.javafro.io.RhythmLoader;
import picosoft.javafro.sequencer.BeepSequencer;
import picosoft.javafro.sequencer.PlaybackSettings;
import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.UncheckedIOException;
import java.io.IOException;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.concurrent.ExecutionException;

/** YAML editor and live controls. UI operations run on the EDT. */
public final class RhythmPlayerPanel extends JPanel {
    interface PlaybackSession {
        void play(Rhythm rhythm, PlaybackSettings settings) throws Exception;
        void stop();
        boolean isStopped();
    }

    private record PlaybackRequest(Rhythm rhythm, PlaybackSettings settings) {}
    private final java.util.function.IntFunction<PlaybackSession> sessionFactory;
    private final RSyntaxTextArea editor = new RSyntaxTextArea(30, 100);
    private final JButton play = new JButton("Play");
    private final JButton stop = new JButton("Stop");
    private final JButton open = new JButton("Otvoriť YAML…");
    private final AppConfig config;
    private final JSlider tempo;
    private final JSlider humanize;
    private final JSlider humanizeProbability;
    private final JLabel status = new JLabel("Pripravené");
    private PlaybackSettings settings;
    private final MixerPanel mixer;
    private JDialog mixerWindow;
    private final DialectControls dialectControls = new DialectControls(dialect -> {
        if (settings != null) settings.setDialect(dialect);
    }, false);
    private String dialectYaml;
    private String selectedRhythmId;
    private boolean loadingYaml;
    private PlaybackSession sequencer;
    private SwingWorker<Void, Void> worker;
    private PlaybackRequest pendingPlayback;

    public RhythmPlayerPanel(String yaml) {
        this(yaml, AppConfig.defaults());
    }

    public RhythmPlayerPanel(String yaml, AppConfig config) {
        this(yaml, config, RhythmPlayerPanel::newSession);
    }

    private static PlaybackSession newSession(int humanizeMaxOffsetMs) {
        BeepSequencer sequencer = new BeepSequencer(humanizeMaxOffsetMs);
        return new PlaybackSession() {
            public void play(Rhythm rhythm, PlaybackSettings settings) throws Exception {
                sequencer.playContinuously(rhythm, settings);
            }
            public void stop() { sequencer.stop(); }
            public boolean isStopped() { return sequencer.isStopped(); }
        };
    }

    RhythmPlayerPanel(String yaml, AppConfig config, java.util.function.IntFunction<PlaybackSession> sessionFactory) {
        super(new BorderLayout(0, 14));
        this.config = config;
        mixer = new MixerPanel(config.defaultTrackVolume());
        this.sessionFactory = sessionFactory;
        tempo = new JSlider(config.minimumTempo(), config.maximumTempo(), config.initialTempo());
        humanize = new JSlider(0, 100, config.humanize());
        humanizeProbability = new JSlider(0, 100, config.humanizeApplianceProbability());
        setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JLabel title = new JLabel("JavAfro · Rhythm player");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JPanel header = new JPanel(new BorderLayout());
        header.add(title, BorderLayout.WEST);
        header.add(open, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        editor.setName("yamlEditor");
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, config.editorFontSize()));
        editor.setTabSize(2);
        editor.setTabsEmulated(true);
        org.fife.ui.rsyntaxtextarea.folding.FoldParserManager.get()
                .addFoldParserMapping(SyntaxConstants.SYNTAX_STYLE_YAML, new RhythmFoldParser());
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_YAML);
        editor.setCodeFoldingEnabled(true);
        editor.setBackground(Color.WHITE);
        editor.setForeground(SandTheme.INK);
        editor.setCaretColor(SandTheme.INK);
        editor.setCurrentLineHighlightColor(SandTheme.CURRENT_LINE);
        editor.setSelectionColor(SandTheme.SELECTION);
        editor.setSelectedTextColor(SandTheme.INK);
        editor.setMargin(new Insets(14, 14, 14, 14));
        editor.setLineWrap(false);
        editor.getAccessibleContext().setAccessibleName("YAML definícia rytmu");
        RTextScrollPane scroll = new RTextScrollPane(editor);
        scroll.setFoldIndicatorEnabled(true);
        scroll.setLineNumbersEnabled(false);
        int foldIconSize = Math.max(10, Math.min(20, editor.getLineHeight() - 2));
        scroll.getGutter().setFoldIcons(new EditorFoldIcon(true, foldIconSize), new EditorFoldIcon(false, foldIconSize));
        scroll.getGutter().setBackground(SandTheme.GUTTER);
        scroll.getGutter().setBorderColor(SandTheme.BORDER);
        scroll.getGutter().setLineNumberColor(SandTheme.MUTED);
        scroll.setBorder(BorderFactory.createTitledBorder("Zdrojový YAML rytmu"));
        add(scroll, BorderLayout.CENTER);

        tempo.setName("tempoSlider");
        humanize.setName("humanizeSlider");
        humanizeProbability.setName("humanizeProbabilitySlider");
        tempo.setMajorTickSpacing(40);
        humanize.setMajorTickSpacing(25);
        tempo.setPaintTicks(true);
        humanize.setPaintTicks(true);
        humanizeProbability.setMajorTickSpacing(25);
        humanizeProbability.setPaintTicks(true);
        JPanel humanizeControls = new JPanel(new GridLayout(2, 1, 0, 8));
        humanizeControls.add(sliderRow("Intenzita", humanize, " %"));
        humanizeControls.add(sliderRow("Pravdepodobnosť", humanizeProbability, " %"));
        JPanel disclosures = new JPanel(new BorderLayout(0, 8));
        disclosures.add(new DisclosurePanel("Humanize", "humanizeDisclosure", humanizeControls), BorderLayout.NORTH);
        disclosures.add(new DisclosurePanel("Dialect", "dialectDisclosure", dialectControls), BorderLayout.SOUTH);
        JPanel liveControls = new JPanel(new BorderLayout(0, 8));
        liveControls.add(sliderRow("Tempo", tempo, " BPM"), BorderLayout.NORTH);
        liveControls.add(disclosures, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        play.setName("playButton");
        stop.setName("stopButton");
        play.setPreferredSize(new Dimension(110, 36));
        stop.setPreferredSize(new Dimension(110, 36));
        stop.setEnabled(false);
        buttons.add(play);
        buttons.add(stop);
        JButton showMixer = new JButton("Mixer…");
        showMixer.setName("mixerButton");
        showMixer.addActionListener(event -> showMixer());
        buttons.add(showMixer);
        status.setName("playbackStatus");
        buttons.add(status);
        JPanel footer = new JPanel(new BorderLayout(0, 12));
        footer.add(liveControls, BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.CENTER);
        footer.add(new JLabel("Tempo a dialect určujú posuvníky. Zmeny YAML sa použijú pri ďalšom Play."), BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        tempo.addChangeListener(event -> { if (settings != null) settings.setBpm(tempo.getValue()); });
        humanize.addChangeListener(event -> { if (settings != null) settings.setHumanize(humanize.getValue()); });
        humanizeProbability.addChangeListener(event -> {
            if (settings != null) settings.setHumanizeApplianceProbability(humanizeProbability.getValue());
        });
        play.addActionListener(event -> startPlayback());
        stop.addActionListener(event -> stopPlayback());
        open.addActionListener(event -> openYaml());
        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { update(); }
            public void removeUpdate(DocumentEvent event) { update(); }
            public void changedUpdate(DocumentEvent event) { update(); }
            private void update() {
                play.setEnabled(!editor.getText().isBlank());
            }
        });
        editor.addCaretListener(event -> {
            if (loadingYaml || worker != null || editor.getText().isBlank()) return;
            try {
                syncRhythm(RhythmLoader.atCaret(editor.getText(), editor.getCaretPosition(), config.initialTempo()));
            } catch (IllegalArgumentException exception) {
                status.setText("Neplatný YAML · " + exception.getMessage());
            }
        });
        setTransferHandler(fileDropHandler(getTransferHandler()));
        editor.setTransferHandler(fileDropHandler(editor.getTransferHandler()));
        scroll.setTransferHandler(fileDropHandler(scroll.getTransferHandler()));
        scroll.getViewport().setTransferHandler(fileDropHandler(scroll.getViewport().getTransferHandler()));
        loadYaml(yaml);
    }

    private void showMixer() {
        if (mixerWindow == null) {
            mixerWindow = new JDialog(SwingUtilities.getWindowAncestor(this), "JavAfro · Mixer", Dialog.ModalityType.MODELESS);
            mixerWindow.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            JPanel content = new JPanel(new BorderLayout());
            content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            content.add(mixer, BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(content);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            mixerWindow.setContentPane(scroll);
            mixerWindow.setMinimumSize(new Dimension(460, 200));
            mixerWindow.setSize(640, 340);
            mixerWindow.setLocationRelativeTo(this);
        }
        mixerWindow.setVisible(true);
        mixerWindow.toFront();
    }

    @Override
    public void removeNotify() {
        if (mixerWindow != null) {
            mixerWindow.dispose();
            mixerWindow = null;
        }
        super.removeNotify();
    }

    private JPanel sliderRow(String text, JSlider slider, String unit) {
        JLabel label = new JLabel(text);
        if (slider == tempo) label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setLabelFor(slider);
        label.setPreferredSize(new Dimension(Math.max(110, label.getPreferredSize().width), 24));
        slider.getAccessibleContext().setAccessibleName(text);
        JLabel value = new JLabel(slider.getValue() + unit, SwingConstants.RIGHT);
        value.setPreferredSize(new Dimension(85, 24));
        slider.addChangeListener(event -> value.setText(slider.getValue() + unit));
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.add(label, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    public static Rhythm parseRhythm(String yaml) {
        var rhythms = RhythmLoader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        return firstRhythm(rhythms);
    }

    public static Rhythm parseRhythm(String yaml, int defaultBpm) {
        var rhythms = RhythmLoader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), defaultBpm);
        return firstRhythm(rhythms);
    }

    private static Rhythm firstRhythm(java.util.Map<String, Rhythm> rhythms) {
        return rhythms.values().iterator().next();
    }

    private void syncRhythm(Rhythm rhythm) {
        mixer.setRhythm(rhythm);
        if (!editor.getText().equals(dialectYaml) || !rhythm.name().equals(selectedRhythmId)) {
            tempo.setMaximum(Math.max(config.maximumTempo(), rhythm.bpm()));
            tempo.setMinimum(Math.min(config.minimumTempo(), rhythm.bpm()));
            tempo.setValue(rhythm.bpm());
            dialectControls.setDialect(rhythm.dialect());
            dialectYaml = editor.getText();
            selectedRhythmId = rhythm.name();
        }
        status.setText("Pripravené · " + rhythm.name());
    }

    private void loadYaml(String yaml) {
        if (yaml.isBlank()) {
            mixer.reset();
            editor.setText("");
            dialectControls.setDialect(picosoft.javafro.base.Dialect.straight(4));
            dialectYaml = "";
            play.setEnabled(false);
            status.setText("Otvor YAML alebo ho presuň do okna");
            return;
        }
        Rhythm rhythm = parseRhythm(yaml, config.initialTempo());
        mixer.reset();
        loadingYaml = true;
        try {
            editor.setText(yaml);
            editor.setCaretPosition(0);
            var folds = editor.getFoldManager();
            folds.reparse();
            for (int i = 0; i < folds.getFoldCount(); i++) folds.getFold(i).setCollapsed(true);
            syncRhythm(rhythm);
        } finally { loadingYaml = false; }
    }

    private void openYaml() {
        JFileChooser chooser = new JFileChooser(config.rhythmsDirectory().toFile());
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("YAML rytmy", "yaml", "yml"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                openFile(chooser.getSelectedFile().toPath());
            } catch (Exception exception) { showError(exception); }
        }
    }

    private TransferHandler fileDropHandler(TransferHandler fallback) {
        return new YamlFileTransferHandler(fallback, () -> worker == null, path -> {
            try { openFile(path); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        }, this::showError);
    }

    private void openFile(Path path) throws IOException {
        if (worker != null) throw new IllegalStateException("Pred otvorením iného rytmu zastav prehrávanie.");
        loadYaml(Files.readString(path, StandardCharsets.UTF_8));
    }

    private void startPlayback() {
        final Rhythm rhythm;
        try { rhythm = RhythmLoader.atCaret(editor.getText(), editor.getCaretPosition(), config.initialTempo()); }
        catch (IllegalArgumentException exception) { showError(exception); return; }
        settings = null; // The previous worker keeps its own controls until stopped.
        syncRhythm(rhythm);
        settings = new PlaybackSettings(tempo.getValue(), humanize.getValue(), dialectControls.value());
        settings.setHumanizeApplianceProbability(humanizeProbability.getValue());
        mixer.bind(settings);
        PlaybackRequest request = new PlaybackRequest(rhythm, settings);
        if (worker != null) {
            pendingPlayback = request; // Repeated clicks keep only the latest request.
            status.setText("Prepínam · " + rhythm.name());
            stop.setEnabled(true);
            sequencer.stop();
            return;
        }
        beginPlayback(request);
    }

    private void beginPlayback(PlaybackRequest request) {
        Rhythm rhythm = request.rhythm();
        PlaybackSession session = sessionFactory.apply(config.humanizeMaxOffsetMs());
        PlaybackSettings controls = request.settings();
        settings = controls;
        sequencer = session;
        play.setEnabled(!editor.getText().isBlank());
        stop.setEnabled(true);
        open.setEnabled(false);
        status.setText("Prehráva sa · " + rhythm.name());
        worker = new SwingWorker<>() {
            protected Void doInBackground() throws Exception {
                session.play(rhythm, controls);
                return null;
            }
            protected void done() {
                Throwable failure = null;
                try { get(); }
                catch (ExecutionException exception) {
                    if (!session.isStopped()) failure = exception.getCause();
                } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                worker = null;
                sequencer = null;
                settings = null;
                PlaybackRequest next = pendingPlayback;
                pendingPlayback = null;
                if (next != null) {
                    beginPlayback(next);
                    return;
                }
                play.setEnabled(!editor.getText().isBlank());
                stop.setEnabled(false);
                open.setEnabled(true);
                status.setText("Zastavené");
                if (failure != null) showError(failure);
            }
        };
        worker.execute();
    }

    /** Stops audio without waiting for the background worker. Also used on window close. */
    public void stopPlayback() {
        pendingPlayback = null;
        if (sequencer != null) {
            status.setText("Zastavujem…");
            stop.setEnabled(false);
            sequencer.stop();
        }
    }

    private void showError(Throwable exception) {
        status.setText("Chyba · skontroluj YAML alebo zvukový výstup");
        JTextArea message = new JTextArea(exception.getMessage() == null ? exception.toString() : exception.getMessage(), 8, 65);
        message.setEditable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(message), "Rytmus sa nepodarilo prehrať", JOptionPane.ERROR_MESSAGE);
    }
}
