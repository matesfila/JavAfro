package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.TextMode;
import picosoft.javafro.AppInfo;
import picosoft.javafro.base.Rhythm;
import picosoft.javafro.config.AppConfig;
import picosoft.javafro.io.RhythmLoader;
import picosoft.javafro.io.DesktopDraftStore;
import picosoft.javafro.io.DesktopDraftStore.Draft;
import picosoft.javafro.io.RhythmExport;
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
        boolean queue(Rhythm rhythm, PlaybackSettings settings, Runnable onStarted);
        void cancelQueued();
        void stop();
        boolean isStopped();
    }

    private record PlaybackRequest(Rhythm rhythm, PlaybackSettings settings, String source) {}
    private final java.util.function.IntFunction<PlaybackSession> sessionFactory;
    private final RSyntaxTextArea editor = new RSyntaxTextArea(30, 100);
    private final JButton play = new JButton("Play");
    private final JButton stop = new JButton("Stop");
    private final JButton open = new JButton("Otvoriť YAML…");
    private final AppConfig config;
    private final JSlider tempo;
    private final JSlider humanize;
    private final JSlider humanizeProbability;
    private final JLabel status = new JLabel("");
    private PlaybackSettings settings;
    private final MixerPanel mixer;
    private JDialog mixerWindow;
    private final DialectControls dialectControls = new DialectControls(dialect -> {
        if (settings != null) settings.setDialect(dialect);
        scheduleDraft();
    }, false);
    private String dialectYaml;
    private String selectedRhythmId;
    private boolean loadingYaml;
    private PlaybackSession sequencer;
    private SwingWorker<Void, Void> worker;
    private PlaybackRequest pendingPlayback;
    private Rhythm selectedDefinition;
    private String savedSource;
    private Path currentFile;
    private Draft previousDocument;
    private DesktopDraftStore draftStore;
    private final JLabel draftStatus = new JLabel("");
    private final Timer draftTimer = new Timer(400, event -> persistDraft());
    private final JMenuItem restorePrevious = new JMenuItem("Obnoviť predchádzajúci dokument");

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
            public boolean queue(Rhythm rhythm, PlaybackSettings settings, Runnable onStarted) {
                return sequencer.queueRhythm(rhythm, settings, onStarted);
            }
            public void cancelQueued() { sequencer.cancelQueuedRhythm(); }
            public void stop() { sequencer.stop(); }
            public boolean isStopped() { return sequencer.isStopped(); }
        };
    }

    RhythmPlayerPanel(String yaml, AppConfig config, java.util.function.IntFunction<PlaybackSession> sessionFactory) {
        super(new BorderLayout(0, 10));
        this.config = config;
        mixer = new MixerPanel(config.defaultTrackVolume());
        this.sessionFactory = sessionFactory;
        tempo = new TempoDial(config.minimumTempo(), config.maximumTempo(), config.initialTempo());
        humanize = new JSlider(0, 100, config.humanize());
        humanizeProbability = new JSlider(0, 100, config.humanizeApplianceProbability());
        draftTimer.setRepeats(false);
        draftStatus.setName("draftStatus");
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JLabel title = new JLabel("JavAfro · Rhythm player");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JPanel header = new JPanel(new BorderLayout());
        header.add(title, BorderLayout.WEST);
        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton about = new JButton("O aplikácii");
        about.setName("aboutButton");
        about.addActionListener(event -> JOptionPane.showMessageDialog(this, new String[]{
                "JavAfro · verzia " + AppInfo.version(),
                "Autor: " + AppInfo.creator(),
                "Pomocník pri vývoji: " + AppInfo.assistant()
        }, "O aplikácii", JOptionPane.INFORMATION_MESSAGE));
        headerActions.add(open);
        JButton fileMenu = new JButton("Súbor"); fileMenu.setName("fileMenuButton");
        JPopupMenu files = new JPopupMenu();
        JMenuItem newDocument = new JMenuItem("Nový rytmus");
        newDocument.addActionListener(event -> {
            if (worker != null) { status.setText("Najprv stlač Stop"); return; }
            String blank = "\"New rhythm\":\n  bpm: " + config.initialTempo() + "\n  timeSignature: \"4/4\"\n  stepsPerBeat: 4\n  tracks:\n    djembe: \"b.tt..s.\"\n";
            replaceDocument(new Draft(blank, "", 0, blank, null));
        });
        JMenuItem save = new JMenuItem("Uložiť"); save.addActionListener(event -> saveDocument(false));
        JMenuItem saveAs = new JMenuItem("Uložiť ako…"); saveAs.addActionListener(event -> saveDocument(true));
        restorePrevious.setEnabled(false);
        restorePrevious.addActionListener(event -> { if (previousDocument != null && worker == null) replaceDocument(previousDocument); });
        files.add(newDocument); files.add(save); files.add(saveAs); files.add(restorePrevious);
        fileMenu.addActionListener(event -> {
            newDocument.setEnabled(worker == null);
            restorePrevious.setEnabled(worker == null && previousDocument != null);
            files.show(fileMenu, 0, fileMenu.getHeight());
        });
        headerActions.add(fileMenu);
        headerActions.add(about);
        header.add(headerActions, BorderLayout.EAST);
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
        JToggleButton editMode = new JToggleButton("Prepisovanie");
        editMode.setName("insertModeButton");
        editMode.setToolTipText("Prepnúť vkladanie a prepisovanie znakov (Insert)");
        editMode.setFocusable(false);
        editMode.addActionListener(event -> {
            boolean overwrite = editMode.isSelected();
            editor.setTextMode(overwrite ? TextMode.OVERWRITE : TextMode.INSERT);
            editor.requestFocusInWindow();
        });
        editor.getInputMap().put(KeyStroke.getKeyStroke("INSERT"), "toggleEditMode");
        editor.getActionMap().put("toggleEditMode", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { editMode.doClick(); }
        });
        editor.getAccessibleContext().setAccessibleName("YAML definícia rytmu");
        RTextScrollPane scroll = new RTextScrollPane(editor);
        scroll.setFoldIndicatorEnabled(true);
        scroll.setLineNumbersEnabled(false);
        int foldIconSize = Math.max(10, Math.min(20, editor.getLineHeight() - 2));
        scroll.getGutter().setFoldIcons(new EditorFoldIcon(true, foldIconSize), new EditorFoldIcon(false, foldIconSize));
        // The library also uses this color for separators below collapsed folds.
        // Our chevrons paint their own color, so keep only the separators transparent.
        scroll.getGutter().setFoldIndicatorForeground(new Color(0, 0, 0, 0));
        scroll.getGutter().setBackground(SandTheme.GUTTER);
        scroll.getGutter().setBorderColor(SandTheme.BORDER);
        scroll.getGutter().setLineNumberColor(SandTheme.MUTED);
        scroll.setBorder(BorderFactory.createTitledBorder("Zdrojový YAML rytmu"));
        JPanel editorToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        editorToolbar.add(editMode);
        JPanel editorPanel = new JPanel(new BorderLayout(0, 6));
        editorPanel.add(editorToolbar, BorderLayout.NORTH);
        editorPanel.add(scroll, BorderLayout.CENTER);
        editorPanel.add(draftStatus, BorderLayout.SOUTH);
        add(editorPanel, BorderLayout.CENTER);

        tempo.setName("tempoSlider");
        humanize.setName("humanizeSlider");
        humanizeProbability.setName("humanizeProbabilitySlider");
        humanize.setMajorTickSpacing(25);
        humanize.setPaintTicks(true);
        humanizeProbability.setMajorTickSpacing(25);
        humanizeProbability.setPaintTicks(true);
        TouchSliders.install(humanize); TouchSliders.install(humanizeProbability);
        JPanel humanizeControls = new JPanel(new GridLayout(2, 1, 0, 8));
        humanizeControls.add(sliderRow("Intenzita", humanize, " %"));
        humanizeControls.add(sliderRow("Pravdepodobnosť", humanizeProbability, " %"));
        JPanel disclosures = new JPanel(new BorderLayout(0, 8));
        disclosures.add(new DisclosurePanel("Humanize", "humanizeDisclosure", humanizeControls), BorderLayout.NORTH);
        disclosures.add(new DisclosurePanel("Dialect", "dialectDisclosure", dialectControls), BorderLayout.SOUTH);
        JPanel liveControls = new JPanel(new BorderLayout(0, 8));
        liveControls.setName("liveControls");
        liveControls.add(TempoDial.controls(tempo), BorderLayout.NORTH);
        liveControls.add(disclosures, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        play.setName("playButton");
        stop.setName("stopButton");
        open.setName("openButton");
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
        JPanel transportRow = new JPanel(new BorderLayout(10, 0));
        transportRow.add(buttons, BorderLayout.WEST); transportRow.add(status, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.add(liveControls, BorderLayout.NORTH);
        footer.add(transportRow, BorderLayout.CENTER);
        JPanel notes = new JPanel(new GridLayout(0, 1, 0, 6));
        JLabel copyright = new JLabel("© 2026 " + AppInfo.creator(), SwingConstants.CENTER);
        copyright.setFont(copyright.getFont().deriveFont(Font.PLAIN, 11f));
        copyright.setForeground(SandTheme.MUTED);
        notes.add(copyright);
        footer.add(notes, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        tempo.addChangeListener(event -> { if (settings != null) settings.setBpm(tempo.getValue()); scheduleDraft(); });
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
                scheduleDraft();
            }
        });
        editor.addCaretListener(event -> {
            scheduleDraft();
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
        savedSource = yaml;
        shortcut("control S", "saveDocument", () -> saveDocument(false));
        shortcut("control shift S", "saveDocumentAs", () -> saveDocument(true));
        shortcut("control O", "openDocument", this::openYaml);
        shortcut("control N", "newDocument", () -> newDocument.doClick());
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
        label.setFont(label.getFont().deriveFont(slider == tempo ? Font.BOLD : Font.PLAIN, 15f));
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
        selectedDefinition = rhythm;
        mixer.setRhythm(rhythm);
        if (!editor.getText().equals(dialectYaml) || !rhythm.name().equals(selectedRhythmId)) {
            tempo.setMaximum(Math.max(config.maximumTempo(), rhythm.bpm()));
            tempo.setMinimum(Math.min(config.minimumTempo(), rhythm.bpm()));
            tempo.setValue(rhythm.bpm());
            dialectControls.setDialect(rhythm.dialect());
            dialectYaml = editor.getText();
            selectedRhythmId = rhythm.name();
        }
        status.setText("");
    }

    private void loadYaml(String yaml) {
        selectedDefinition = null; selectedRhythmId = null; dialectYaml = null;
        mixer.reset();
        loadingYaml = true;
        try {
            editor.setText(yaml); editor.setCaretPosition(0); editor.discardAllEdits();
            if (yaml.isBlank()) {
                dialectControls.setDialect(picosoft.javafro.base.Dialect.straight(4));
                play.setEnabled(false);
                status.setText("Otvor YAML alebo ho presuň do okna");
                return;
            }
            Rhythm rhythm = parseRhythm(yaml, config.initialTempo());
            var folds = editor.getFoldManager();
            folds.reparse();
            for (int i = 0; i < folds.getFoldCount(); i++) folds.getFold(i).setCollapsed(true);
            syncRhythm(rhythm);
        } catch (IllegalArgumentException exception) {
            play.setEnabled(!yaml.isBlank()); status.setText("Neplatný YAML · text môžeš upraviť alebo uložiť");
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
        String source = Files.readString(path, StandardCharsets.UTF_8);
        replaceDocument(new Draft(source, path.toAbsolutePath().normalize().toString(), 0, source, null));
    }

    private void shortcut(String key, String name, Runnable action) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); }
        });
    }

    /** Enabled only by the actual application launcher; embedded panels and tests stay isolated. */
    public void enableDraftRecovery(Path directory, Path initialFile) {
        currentFile = initialFile == null ? null : initialFile.toAbsolutePath().normalize();
        try {
            draftStore = new DesktopDraftStore(directory);
            if (draftStore.current() != null) {
                if (initialFile == null) applyDraft(draftStore.current());
                else draftStore.save(snapshotDraft(), true); // The explicit file wins; keep the old draft as a backup.
            }
            previousDocument = draftStore.previous();
            restorePrevious.setEnabled(previousDocument != null);
            persistDraft();
        } catch (IOException | RuntimeException exception) {
            draftStore = null;
            draftError(exception);
        }
    }

    private void scheduleDraft() {
        if (!loadingYaml && draftStore != null && savedSource != null) draftTimer.restart();
    }

    Draft snapshotDraft() {
        DesktopDraftStore.Controls controls = null;
        if (selectedDefinition != null && editor.getText().equals(dialectYaml)
                && (tempo.getValue() != selectedDefinition.bpm() || !dialectControls.value().equals(selectedDefinition.dialect()))) {
            controls = new DesktopDraftStore.Controls(selectedDefinition.name(), tempo.getValue(), dialectControls.value().offsets());
        }
        return new Draft(editor.getText(), currentFile == null ? "" : currentFile.toString(),
                editor.getCaretPosition(), savedSource == null ? editor.getText() : savedSource, controls);
    }

    private void persistDraft() {
        if (draftStore == null) return;
        try {
            draftStore.save(snapshotDraft(), false);
            draftStatus.setText((currentFile == null ? "rytmus.yaml" : currentFile.getFileName().toString())
                    + (snapshotDraft().dirty() ? " · rozpracovaný koncept uložený" : " · koncept uložený"));
            draftStatus.setToolTipText(null);
        } catch (IOException exception) { draftError(exception); }
    }

    private void draftError(Exception exception) {
        draftStatus.setText("Koncept sa nepodarilo uložiť alebo obnoviť. Ulož prácu cez Súbor → Uložiť.");
        draftStatus.setToolTipText(exception.getMessage());
    }

    private void applyDraft(Draft draft) {
        Path path = draft.file().isEmpty() ? null : Path.of(draft.file());
        loadYaml(draft.source());
        loadingYaml = true;
        try {
            currentFile = path; savedSource = draft.savedSource();
            if (!draft.source().isBlank()) {
                try {
                    Rhythm selected = RhythmLoader.atCaret(draft.source(), draft.caret(), config.initialTempo());
                    if (draft.controls() != null) {
                        var rhythms = RhythmLoader.load(new ByteArrayInputStream(draft.source().getBytes(StandardCharsets.UTF_8)), config.initialTempo());
                        selected = rhythms.getOrDefault(draft.controls().rhythm(), selected);
                    }
                    syncRhythm(selected);
                    if (draft.controls() != null && selected.name().equals(draft.controls().rhythm())) {
                        int bpm = draft.controls().bpm();
                        tempo.setMinimum(Math.min(tempo.getMinimum(), bpm)); tempo.setMaximum(Math.max(tempo.getMaximum(), bpm)); tempo.setValue(bpm);
                        if (draft.controls().dialect().size() == selected.stepsPerBeat()) {
                            dialectControls.setDialect(picosoft.javafro.base.Dialect.of(selected.stepsPerBeat(), draft.controls().dialect()));
                        }
                    }
                } catch (IllegalArgumentException exception) { status.setText("Neplatný YAML · text môžeš upraviť alebo uložiť"); }
            }
            editor.setCaretPosition(draft.caret());
        } finally { loadingYaml = false; }
    }

    private boolean confirmDocumentChange() {
        return DocumentGuard.approve(snapshotDraft().dirty(), () -> {
            Object[] options = {"Zrušiť", "Uložiť a pokračovať", "Pokračovať bez uloženia"};
            int choice = JOptionPane.showOptionDialog(this, "Dokument má neuložené zmeny.", "Rozpracovaný rytmus",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            return switch (choice) { case 1 -> DocumentGuard.Decision.SAVE; case 2 -> DocumentGuard.Decision.DISCARD; default -> DocumentGuard.Decision.CANCEL; };
        }, () -> saveDocument(false));
    }

    private boolean replaceDocument(Draft next) {
        if (!confirmDocumentChange()) return false;
        Draft before = snapshotDraft();
        if (draftStore != null) {
            try { draftStore.save(before, false); draftStore.save(next, true); }
            catch (IOException exception) { draftError(exception); }
        }
        previousDocument = before;
        applyDraft(next);
        restorePrevious.setEnabled(true);
        scheduleDraft();
        return true;
    }

    private boolean saveDocument(boolean saveAs) {
        Path target = saveAs ? null : currentFile;
        if (target == null) {
            JFileChooser chooser = new JFileChooser(currentFile == null ? config.rhythmsDirectory().toFile() : currentFile.getParent().toFile());
            chooser.setSelectedFile(currentFile == null ? new java.io.File("rytmus.yaml") : currentFile.toFile());
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
            target = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            String name = target.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (!name.endsWith(".yaml") && !name.endsWith(".yml")) target = target.resolveSibling(target.getFileName() + ".yaml");
            if (Files.exists(target) && JOptionPane.showConfirmDialog(this, "Nahradiť existujúci súbor?", "Uložiť YAML", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
        }
        try {
            if (!saveAs && currentFile != null && target.equals(currentFile) && Files.exists(target)
                    && !Files.readString(target).equals(savedSource)
                    && JOptionPane.showConfirmDialog(this, "Súbor na disku sa zmenil. Prepísať ho?", "Zmenený súbor", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
            saveTo(target); return true;
        } catch (IOException exception) { showError(exception); return false; }
    }

    void saveTo(Path target) throws IOException {
        Draft current = snapshotDraft();
        String exported = RhythmExport.export(current.source(), current.controls(), config.initialTempo());
        target = target.toAbsolutePath().normalize();
        Path temporary = Files.createTempFile(target.getParent(), "javafro-save-", ".tmp");
        try {
            Files.writeString(temporary, exported);
            try { Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException exception) { Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        loadingYaml = true;
        try {
            if (!exported.equals(editor.getText())) {
                editor.beginAtomicEdit();
                try { editor.replaceRange(exported, 0, editor.getDocument().getLength()); editor.setCaretPosition(Math.min(current.caret(), exported.length())); }
                finally { editor.endAtomicEdit(); }
            }
            currentFile = target; savedSource = exported; dialectYaml = exported;
            try {
                var rhythms = RhythmLoader.load(new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)), config.initialTempo());
                selectedDefinition = rhythms.get(selectedRhythmId);
            } catch (IllegalArgumentException exception) { selectedDefinition = null; }
        } finally { loadingYaml = false; }
        persistDraft();
    }

    public boolean requestClose() {
        if (!confirmDocumentChange()) return false;
        flushDraft(); stopPlayback(); return true;
    }

    public void flushDraft() { draftTimer.stop(); persistDraft(); }

    private void startPlayback() {
        pendingPlayback = null;
        if (sequencer != null) sequencer.cancelQueued();
        final Rhythm rhythm;
        try {
            rhythm = RhythmLoader.atCaret(editor.getText(), editor.getCaretPosition(), config.initialTempo());
            requestPlayback(rhythm);
        }
        catch (IllegalArgumentException exception) { pendingPlayback = null; showError(exception); }
    }

    private void requestPlayback(Rhythm rhythm) {
        boolean same = editor.getText().equals(dialectYaml) && rhythm.name().equals(selectedRhythmId);
        PlaybackSettings controls = new PlaybackSettings(same ? tempo.getValue() : rhythm.bpm(),
                humanize.getValue(), same ? dialectControls.value() : rhythm.dialect());
        controls.setHumanizeApplianceProbability(humanizeProbability.getValue());
        mixer.configure(controls, rhythm);
        PlaybackRequest request = new PlaybackRequest(rhythm, controls, editor.getText());
        if (worker != null) {
            pendingPlayback = request; // Repeated clicks keep only the latest request.
            PlaybackSession session = sequencer;
            session.queue(rhythm, controls, () -> SwingUtilities.invokeLater(() -> {
                if (sequencer != session || session.isStopped() || pendingPlayback != request) return;
                pendingPlayback = null;
                applyPlaybackRequest(request);
                status.setText("");
            }));
            status.setText("Po skončení patternu: " + rhythm.name());
            stop.setEnabled(true);
            return;
        }
        beginPlayback(request);
    }

    private void beginPlayback(PlaybackRequest request) {
        Rhythm rhythm = request.rhythm();
        PlaybackSession session = sessionFactory.apply(config.humanizeMaxOffsetMs());
        PlaybackSettings controls = request.settings();
        applyPlaybackRequest(request);
        sequencer = session;
        play.setEnabled(!editor.getText().isBlank());
        stop.setEnabled(true);
        open.setEnabled(false);
        status.setText("");
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
                if (next != null && failure == null && session.isStopped()) {
                    beginPlayback(next);
                    return;
                }
                play.setEnabled(!editor.getText().isBlank());
                stop.setEnabled(false);
                open.setEnabled(true);
                status.setText("");
                if (failure != null) showError(failure);
            }
        };
        worker.execute();
    }

    private void applyPlaybackRequest(PlaybackRequest request) {
        settings = null; // Updating widgets must not mutate the previous rhythm's controls.
        syncRhythm(request.rhythm());
        dialectYaml = request.source();
        PlaybackSettings controls = request.settings();
        tempo.setValue(controls.snapshot().bpm());
        dialectControls.setDialect(controls.snapshot().dialect());
        humanize.setValue(controls.snapshot().humanize());
        humanizeProbability.setValue(controls.humanizeApplianceProbability());
        settings = controls;
        mixer.bind(controls);
    }

    /** Stops audio without waiting for the background worker. Also used on window close. */
    public void stopPlayback() {
        pendingPlayback = null;
        if (sequencer != null) {
            status.setText("");
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
