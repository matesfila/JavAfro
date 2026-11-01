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
    private final RSyntaxTextArea editor = new RSyntaxTextArea(30, 100);
    private final JButton play = new JButton("Play");
    private final JButton stop = new JButton("Stop");
    private final JButton open = new JButton("Otvoriť YAML…");
    private final AppConfig config;
    private final JSlider tempo;
    private final JSlider humanize;
    private final JLabel status = new JLabel("Pripravené");
    private PlaybackSettings settings;
    private final DialectControls dialectControls = new DialectControls(dialect -> {
        if (settings != null) settings.setDialect(dialect);
    });
    private String dialectYaml;
    private BeepSequencer sequencer;
    private SwingWorker<Void, Void> worker;

    public RhythmPlayerPanel(String yaml) {
        this(yaml, AppConfig.defaults());
    }

    public RhythmPlayerPanel(String yaml, AppConfig config) {
        super(new BorderLayout(0, 14));
        this.config = config;
        tempo = new JSlider(config.minimumTempo(), config.maximumTempo(), config.initialTempo());
        humanize = new JSlider(0, 100, config.humanize());
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
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_YAML);
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
        scroll.setLineNumbersEnabled(true);
        scroll.getGutter().setBackground(SandTheme.GUTTER);
        scroll.getGutter().setBorderColor(SandTheme.BORDER);
        scroll.getGutter().setLineNumberColor(SandTheme.MUTED);
        scroll.setBorder(BorderFactory.createTitledBorder("Zdrojový YAML rytmu"));
        add(scroll, BorderLayout.CENTER);

        tempo.setName("tempoSlider");
        humanize.setName("humanizeSlider");
        tempo.setMajorTickSpacing(40);
        humanize.setMajorTickSpacing(25);
        tempo.setPaintTicks(true);
        humanize.setPaintTicks(true);
        JPanel sliders = new JPanel(new GridLayout(2, 1, 0, 8));
        sliders.add(sliderRow("Tempo", tempo, " BPM"));
        sliders.add(sliderRow("Humanize", humanize, " %"));
        JPanel liveControls = new JPanel(new BorderLayout(0, 8));
        liveControls.add(sliders, BorderLayout.NORTH);
        liveControls.add(dialectControls, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        play.setName("playButton");
        stop.setName("stopButton");
        play.setPreferredSize(new Dimension(110, 36));
        stop.setPreferredSize(new Dimension(110, 36));
        stop.setEnabled(false);
        buttons.add(play);
        buttons.add(stop);
        status.setName("playbackStatus");
        buttons.add(status);
        JPanel footer = new JPanel(new BorderLayout(0, 12));
        footer.add(liveControls, BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.CENTER);
        footer.add(new JLabel("Tempo a dialect určujú posuvníky. Zmeny YAML sa použijú pri ďalšom Play."), BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        tempo.addChangeListener(event -> { if (settings != null) settings.setBpm(tempo.getValue()); });
        humanize.addChangeListener(event -> { if (settings != null) settings.setHumanize(humanize.getValue()); });
        play.addActionListener(event -> startPlayback());
        stop.addActionListener(event -> stopPlayback());
        open.addActionListener(event -> openYaml());
        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { update(); }
            public void removeUpdate(DocumentEvent event) { update(); }
            public void changedUpdate(DocumentEvent event) { update(); }
            private void update() {
                if (worker == null) play.setEnabled(!editor.getText().isBlank());
            }
        });
        setTransferHandler(fileDropHandler(getTransferHandler()));
        editor.setTransferHandler(fileDropHandler(editor.getTransferHandler()));
        scroll.setTransferHandler(fileDropHandler(scroll.getTransferHandler()));
        scroll.getViewport().setTransferHandler(fileDropHandler(scroll.getViewport().getTransferHandler()));
        loadYaml(yaml);
    }

    private JPanel sliderRow(String text, JSlider slider, String unit) {
        JLabel label = new JLabel(text);
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
        return singleRhythm(rhythms);
    }

    public static Rhythm parseRhythm(String yaml, int defaultBpm) {
        var rhythms = RhythmLoader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), defaultBpm);
        return singleRhythm(rhythms);
    }

    private static Rhythm singleRhythm(java.util.Map<String, Rhythm> rhythms) {
        if (rhythms.size() != 1) throw new IllegalArgumentException("Editor musí obsahovať práve jeden rytmus.");
        return rhythms.values().iterator().next();
    }

    private void loadYaml(String yaml) {
        if (yaml.isBlank()) {
            editor.setText("");
            dialectControls.setDialect(picosoft.javafro.base.Dialect.straight(4));
            dialectYaml = "";
            play.setEnabled(false);
            status.setText("Otvor YAML alebo ho presuň do okna");
            return;
        }
        Rhythm rhythm = parseRhythm(yaml, config.initialTempo());
        editor.setText(yaml);
        editor.setCaretPosition(0);
        tempo.setMaximum(Math.max(config.maximumTempo(), rhythm.bpm()));
        tempo.setMinimum(Math.min(config.minimumTempo(), rhythm.bpm()));
        tempo.setValue(rhythm.bpm());
        dialectControls.setDialect(rhythm.dialect());
        dialectYaml = yaml;
        status.setText("Pripravené · " + rhythm.name());
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
        if (worker != null) return;
        final Rhythm rhythm;
        try { rhythm = parseRhythm(editor.getText(), config.initialTempo()); }
        catch (IllegalArgumentException exception) { showError(exception); return; }
        if (!editor.getText().equals(dialectYaml)) {
            dialectControls.setDialect(rhythm.dialect());
            dialectYaml = editor.getText();
        }
        settings = new PlaybackSettings(tempo.getValue(), humanize.getValue(), dialectControls.value());
        BeepSequencer session = new BeepSequencer(config.humanizeMaxOffsetMs());
        PlaybackSettings controls = settings;
        sequencer = session;
        play.setEnabled(false);
        stop.setEnabled(true);
        open.setEnabled(false);
        status.setText("Prehráva sa · " + rhythm.name());
        worker = new SwingWorker<>() {
            protected Void doInBackground() throws Exception {
                session.playContinuously(rhythm, controls);
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
