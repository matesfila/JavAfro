package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
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
    private final SpacedSyntaxTextArea editor = new SpacedSyntaxTextArea(30, 50);
    private final JButton play = new JButton("Play");
    private final JButton stop = new JButton("Stop");
    private final JButton open = new JButton("Otvoriť");
    private final RTextScrollPane editorScroll = new RTextScrollPane(editor);
    private final NotePad notePad;
    private final JLabel fileName = new JLabel("rytmus.yaml");
    private final JLabel rhythmName = new JLabel("Rytmus");
    private final JLabel editorStatus = new JLabel("");
    private final AppConfig config;
    private final JSlider tempo;
    private final JSlider humanize;
    private final JSlider humanizeProbability;
    private final JLabel status = new JLabel("");
    private PlaybackSettings settings;
    private final MixerPanel mixer;
    private final DialectControls dialectControls = new DialectControls(dialect -> {
        if (settings != null) settings.setDialect(dialect);
        updateFileName();
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
        mixer = new MixerPanel(config.defaultTrackVolume(), config.defaultMasterVolume());
        this.sessionFactory = sessionFactory;
        tempo = new TempoDial(config.minimumTempo(), config.maximumTempo(), config.initialTempo());
        humanize = new JSlider(0, 100, config.humanize());
        humanizeProbability = new JSlider(0, 100, config.humanizeApplianceProbability());
        notePad = new NotePad(editor);
        draftTimer.setRepeats(false);
        draftStatus.setName("draftStatus");
        setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        add(buildHeader(), BorderLayout.NORTH);
        JPanel workspace = new JPanel(new BorderLayout(16, 0));
        workspace.setOpaque(false);
        workspace.add(buildEditorCard(), BorderLayout.CENTER);
        workspace.add(buildPlaybackCard(), BorderLayout.EAST);
        add(workspace, BorderLayout.CENTER);
        JLabel copyright = new JLabel("© 2026 " + AppInfo.creator(), SwingConstants.CENTER);
        copyright.setFont(copyright.getFont().deriveFont(Font.PLAIN, 11f));
        copyright.setForeground(SandTheme.MUTED);
        add(copyright, BorderLayout.SOUTH);

        tempo.addChangeListener(event -> { if (settings != null) settings.setBpm(tempo.getValue()); scheduleDraft(); updateFileName(); });
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
                if (worker != null && !loadingYaml) setEditorStatus("Upravené · stlač Play", false);
                updateFileName();
                scheduleDraft();
            }
        });
        editor.addCaretListener(event -> {
            scheduleDraft();
            if (notePad.isVisible()) updateNotePad();
            if (loadingYaml || worker != null || editor.getText().isBlank()) return;
            try {
                syncRhythm(RhythmLoader.atCaret(editor.getText(), editor.getCaretPosition(), config.initialTempo()));
            } catch (IllegalArgumentException exception) {
                setEditorStatus("Neplatný YAML · " + exception.getMessage(), true);
            }
        });
        setTransferHandler(fileDropHandler(getTransferHandler()));
        editor.setTransferHandler(fileDropHandler(editor.getTransferHandler()));
        editorScroll.setTransferHandler(fileDropHandler(editorScroll.getTransferHandler()));
        editorScroll.getViewport().setTransferHandler(fileDropHandler(editorScroll.getViewport().getTransferHandler()));
        loadYaml(yaml);
        savedSource = yaml;
        updateFileName();
        shortcut("control S", "saveDocument", () -> saveDocument(false));
        shortcut("control shift S", "saveDocumentAs", () -> saveDocument(true));
        shortcut("control O", "openDocument", this::openYaml);
        shortcut("control N", "newDocument", this::newDocument);
    }

    /** Brand on the left; Open and the Súbor menu on the right (web site-header). */
    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 2, 2, 0));
        JLabel brand = new JLabel("JavAfro", UiIcons.logo(30), SwingConstants.LEFT);
        brand.setName("brand");
        brand.setIconTextGap(10);
        brand.setFont(SandTheme.heading(brand.getFont(), 23f));
        header.add(brand, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        open.setIcon(UiIcons.icon(UiIcons.Shape.FOLDER, 18));
        open.setToolTipText("Otvoriť YAML súbor (Ctrl+O)");
        actions.add(open);
        JButton fileMenu = new JButton("Súbor");
        fileMenu.setName("fileMenuButton");
        JPopupMenu files = new JPopupMenu();
        JMenuItem newDocument = menuItem("Nový rytmus", UiIcons.Shape.NEW, "control N", event -> newDocument());
        JMenuItem openItem = menuItem("Otvoriť YAML…", UiIcons.Shape.FOLDER, "control O", event -> openYaml());
        JMenuItem save = menuItem("Uložiť", UiIcons.Shape.DOWNLOAD, "control S", event -> saveDocument(false));
        JMenuItem saveAs = menuItem("Uložiť ako…", null, "control shift S", event -> saveDocument(true));
        restorePrevious.setEnabled(false);
        restorePrevious.addActionListener(event -> { if (previousDocument != null && worker == null) replaceDocument(previousDocument); });
        JMenuItem guide = menuItem(HelpDocuments.Document.GUIDE.title, UiIcons.Shape.DOCUMENT, null,
                event -> openHelp(HelpDocuments.Document.GUIDE));
        JMenuItem visualGuide = menuItem(HelpDocuments.Document.VISUAL_GUIDE.title, UiIcons.Shape.DOCUMENT, null,
                event -> openHelp(HelpDocuments.Document.VISUAL_GUIDE));
        JMenuItem about = menuItem("O aplikácii", UiIcons.Shape.INFO, null, event -> AboutDialog.show(this));
        about.setName("aboutButton");
        files.add(newDocument); files.add(openItem); files.add(save); files.add(saveAs); files.add(restorePrevious);
        files.addSeparator();
        files.add(guide); files.add(visualGuide);
        files.addSeparator();
        files.add(about);
        fileMenu.addActionListener(event -> {
            newDocument.setEnabled(worker == null);
            openItem.setEnabled(worker == null);
            restorePrevious.setEnabled(worker == null && previousDocument != null);
            files.show(fileMenu, fileMenu.getWidth() - files.getPreferredSize().width, fileMenu.getHeight() + 2);
        });
        actions.add(fileMenu);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private static JMenuItem menuItem(String text, UiIcons.Shape icon, String accelerator, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        if (icon != null) item.setIcon(UiIcons.icon(icon, 16));
        // Only displayed: the panel's own key bindings perform the shortcut while the menu is closed.
        if (accelerator != null) item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        item.addActionListener(action);
        return item;
    }

    /** Toolbar with the file name and editing modes, the editor, the note keyboard and the status bar (web editor-card). */
    private JComponent buildEditorCard() {
        editor.setName("yamlEditor");
        editor.setLineHeight(config.editorStyle().lineHeight());
        editor.setTabSize(2);
        editor.setTabsEmulated(true);
        org.fife.ui.rsyntaxtextarea.folding.FoldParserManager.get()
                .addFoldParserMapping(SyntaxConstants.SYNTAX_STYLE_YAML, new RhythmFoldParser());
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_YAML);
        editor.setCodeFoldingEnabled(true);
        editor.setBackground(SandTheme.PAPER);
        editor.setForeground(SandTheme.INK);
        editor.setCaretColor(SandTheme.CARET);
        editor.setCurrentLineHighlightColor(SandTheme.CURRENT_LINE);
        editor.setSelectionColor(SandTheme.SELECTION);
        editor.setSelectedTextColor(SandTheme.INK);
        setEditorFont(SandTheme.editorFont(config.editorStyle().fontFamily(), config.editorFontSize()));
        editor.setMargin(new Insets(14, 14, 14, 14));
        editor.setLineWrap(false);
        editor.getAccessibleContext().setAccessibleName("YAML definícia rytmu");

        JToggleButton editMode = toolbarToggle("Prepisovanie", "insertModeButton",
                "Prepnúť vkladanie a prepisovanie znakov (Insert)");
        editMode.addActionListener(event -> {
            boolean overwrite = editMode.isSelected();
            editor.setTextMode(overwrite ? TextMode.OVERWRITE : TextMode.INSERT);
            editor.requestFocusInWindow();
        });
        editor.getInputMap().put(KeyStroke.getKeyStroke("INSERT"), "toggleEditMode");
        editor.getActionMap().put("toggleEditMode", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { editMode.doClick(); }
        });
        JToggleButton notesMode = toolbarToggle("Noty", "notesModeButton",
                "Zobraziť klávesnicu nôt pre nástroj pod kurzorom");
        notePad.setVisible(false);
        notesMode.addActionListener(event -> {
            notePad.setVisible(notesMode.isSelected());
            if (notesMode.isSelected()) updateNotePad();
            revalidate(); repaint();
            editor.requestFocusInWindow();
        });

        editorScroll.setFoldIndicatorEnabled(true);
        editorScroll.setLineNumbersEnabled(false);
        Runnable updateFoldIcons = () -> {
            int foldIconSize = Math.max(10, Math.min(20, editor.getLineHeight() - 2));
            editorScroll.getGutter().setFoldIcons(new EditorFoldIcon(true, foldIconSize), new EditorFoldIcon(false, foldIconSize));
        };
        updateFoldIcons.run();
        EditorZoom.install(editorScroll, editor, size -> {
            setEditorFont(editor.getFont().deriveFont((float) size));
            updateFoldIcons.run();
        });
        // The library also uses this color for separators below collapsed folds.
        // Our chevrons paint their own color, so keep only the separators transparent.
        editorScroll.getGutter().setFoldIndicatorForeground(new Color(0, 0, 0, 0));
        editorScroll.getGutter().setBackground(SandTheme.GUTTER);
        editorScroll.getGutter().setBorderColor(SandTheme.BORDER);
        editorScroll.getGutter().setLineNumberColor(SandTheme.MUTED);
        editorScroll.setBorder(BorderFactory.createEmptyBorder());
        editorScroll.getViewport().setBackground(SandTheme.PAPER);

        fileName.setName("fileName");
        fileName.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        fileName.setForeground(new Color(0x715C49));
        JPanel modes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        modes.setOpaque(false);
        modes.add(notesMode); modes.add(editMode);
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SandTheme.BORDER),
                BorderFactory.createEmptyBorder(5, 12, 5, 8)));
        toolbar.add(fileName, BorderLayout.CENTER);
        toolbar.add(modes, BorderLayout.EAST);

        JPanel surface = new JPanel(new BorderLayout());
        surface.setOpaque(false);
        surface.add(editorScroll, BorderLayout.CENTER);
        surface.add(notePad, BorderLayout.SOUTH);

        editorStatus.setName("editorStatus");
        editorStatus.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        editorStatus.setVisible(false);
        draftStatus.setFont(draftStatus.getFont().deriveFont(draftStatus.getFont().getSize2D() - 1f));
        draftStatus.setForeground(SandTheme.MUTED);
        draftStatus.setBorder(BorderFactory.createEmptyBorder(6, 14, 7, 14));
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setOpaque(false);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, SandTheme.BORDER));
        statusBar.add(editorStatus, BorderLayout.NORTH);
        statusBar.add(draftStatus, BorderLayout.SOUTH);

        JPanel card = SandTheme.card(new JPanel(new BorderLayout()), 1);
        card.setName("editorCard");
        card.add(toolbar, BorderLayout.NORTH);
        card.add(surface, BorderLayout.CENTER);
        card.add(statusBar, BorderLayout.SOUTH);
        return card;
    }

    private static JToggleButton toolbarToggle(String text, String name, String tooltip) {
        JToggleButton button = new JToggleButton(text);
        button.setName(name);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setMargin(new Insets(2, 10, 2, 10));
        button.setFont(button.getFont().deriveFont(button.getFont().getSize2D() - 1f));
        return button;
    }

    /** Rhythm name, transport, tempo and the Mixer / Dialect / Humanize sections (web playback-card). */
    private JComponent buildPlaybackCard() {
        rhythmName.setName("rhythmName");
        rhythmName.setFont(SandTheme.heading(rhythmName.getFont(), 23f));
        play.setName("playButton");
        stop.setName("stopButton");
        open.setName("openButton");
        SandTheme.accent(play);
        SandTheme.key(stop);
        play.setIcon(UiIcons.icon(UiIcons.Shape.PLAY, 20));
        stop.setIcon(UiIcons.icon(UiIcons.Shape.STOP, 20));
        for (JButton button : new JButton[]{play, stop}) {
            button.setFont(SandTheme.heading(button.getFont(), 16f));
            button.setIconTextGap(6);
            button.setPreferredSize(new Dimension(120, 74));
        }
        play.setToolTipText("Prehrať rytmus pod kurzorom; počas prehrávania ho zaradí po skončení patternu");
        stop.setEnabled(false);
        JPanel transport = new JPanel(new GridLayout(1, 2, 6, 0));
        transport.setOpaque(false);
        transport.add(play); transport.add(stop);
        status.setName("playbackStatus");
        status.setForeground(SandTheme.MESSAGE);

        tempo.setName("tempoSlider");
        humanize.setName("humanizeSlider");
        humanizeProbability.setName("humanizeProbabilitySlider");
        TouchSliders.install(humanize); TouchSliders.install(humanizeProbability);
        JPanel humanizeControls = new JPanel(new GridLayout(2, 1, 0, 8));
        humanizeControls.setOpaque(false);
        humanizeControls.add(sliderRow("Miera odchýlky", humanize, " %"));
        humanizeControls.add(sliderRow("Pravdepodobnosť", humanizeProbability, " %"));

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stackAdd(stack, line(rhythmName), 0);
        stackAdd(stack, transport, 10);
        stackAdd(stack, line(status), 4);
        JPanel tempoControls = TempoDial.controls(tempo);
        tempoControls.setName("liveControls");
        stackAdd(stack, tempoControls, 8);
        stackAdd(stack, new DisclosurePanel("Mixer", "mixerDisclosure", mixer), 16);
        stackAdd(stack, new DisclosurePanel("Dialect", "dialectDisclosure", dialectControls), 6);
        stackAdd(stack, new DisclosurePanel("Humanize", "humanizeDisclosure", humanizeControls), 6);

        ColumnPanel column = new ColumnPanel();
        column.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        column.add(stack, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(column, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel card = SandTheme.card(new JPanel(new BorderLayout()), 1);
        card.setName("playbackCard");
        card.getAccessibleContext().setAccessibleName("Prehrávanie");
        card.setPreferredSize(new Dimension(320, 10));
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private static void stackAdd(JPanel stack, JComponent component, int gap) {
        if (gap > 0) stack.add(Box.createVerticalStrut(gap));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(component);
    }

    /** Lets a label shrink to the column width (ellipsis) instead of widening the column. */
    private static JComponent line(JLabel label) {
        JPanel line = new JPanel(new BorderLayout());
        line.setOpaque(false);
        line.add(label, BorderLayout.CENTER);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height + 24));
        return line;
    }

    /** Playback column that always matches the scroll viewport width, so only vertical scrolling is used. */
    private static final class ColumnPanel extends JPanel implements Scrollable {
        ColumnPanel() { super(new BorderLayout()); setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return visible.height - 32; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Editor font and the matching string style (javafro.yaml editor section); used at start and by Ctrl + wheel. */
    private void setEditorFont(Font font) {
        AppConfig.EditorStyle style = config.editorStyle();
        editor.setFont(font);
        // Set the string style after setFont(): RSTA re-derives styled fonts there and drops the letter spacing.
        // The line height stays correct, because letter spacing does not change a font's height.
        Font stringFont = SandTheme.editorStringFont(font, style);
        Color stringColor = new Color(style.stringColor());
        SyntaxScheme scheme = editor.getSyntaxScheme();
        for (int type : new int[] {Token.LITERAL_STRING_DOUBLE_QUOTE, Token.LITERAL_CHAR, Token.LITERAL_BACKQUOTE}) {
            scheme.getStyle(type).foreground = stringColor;
            scheme.getStyle(type).font = stringFont;
        }
        editor.repaint();
    }

    /** Web control-group: label and value on one line, the slider below. */
    private JPanel sliderRow(String text, JSlider slider, String unit) {
        JLabel label = new JLabel(text);
        label.setLabelFor(slider);
        slider.getAccessibleContext().setAccessibleName(text);
        JLabel value = new JLabel(slider.getValue() + unit, SwingConstants.RIGHT);
        value.setFont(SandTheme.heading(value.getFont(), value.getFont().getSize2D() + 3f));
        value.setForeground(new Color(0x795739));
        slider.addChangeListener(event -> value.setText(slider.getValue() + unit));
        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.setOpaque(false);
        heading.add(label, BorderLayout.WEST);
        heading.add(value, BorderLayout.EAST);
        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setOpaque(false);
        row.add(heading, BorderLayout.NORTH);
        row.add(slider, BorderLayout.CENTER);
        return row;
    }

    private void newDocument() {
        if (worker != null) { status.setText("Najprv stlač Stop"); return; }
        String blank = "\"New rhythm\":\n  bpm: " + config.initialTempo() + "\n  timeSignature: \"4/4\"\n  stepsPerBeat: 4\n  tracks:\n    djembe: \"b.tt..s.\"\n";
        replaceDocument(new Draft(blank, "", 0, blank, null));
    }

    private void openHelp(HelpDocuments.Document document) {
        try { new HelpDocuments().open(document); }
        catch (IOException | RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), document.title, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateNotePad() {
        notePad.setInstrument(RhythmLoader.instrumentAtCaret(editor.getText(), editor.getCaretPosition()));
    }

    /** "*name" while the text differs from the last opened or saved version (web editor-toolbar). */
    private void updateFileName() {
        String name = currentFile == null ? "rytmus.yaml" : currentFile.getFileName().toString();
        // Same rule as Draft.dirty(), without building a Draft: document events can fire while the caret is stale.
        boolean modified = savedSource != null && (!editor.getText().equals(savedSource) || controlsPending());
        fileName.setText(modified ? "*" + name : name);
        fileName.setFont(fileName.getFont().deriveFont(modified ? Font.BOLD : Font.PLAIN));
        fileName.setToolTipText(currentFile == null ? null : currentFile.toString());
    }

    /** Validation and editing hints under the editor; hidden when empty. */
    private void setEditorStatus(String text, boolean invalid) {
        editorStatus.setText(text);
        editorStatus.setToolTipText(text.isEmpty() ? null : text);
        editorStatus.setOpaque(invalid);
        editorStatus.setBackground(SandTheme.ERROR_BACKGROUND);
        editorStatus.setForeground(invalid ? SandTheme.ERROR : SandTheme.MUTED);
        editorStatus.setVisible(!text.isEmpty());
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
        setEditorStatus("", false);
        rhythmName.setText(rhythm.name());
        rhythmName.setToolTipText(rhythm.name());
        updateFileName(); // Tempo and dialect listeners fired while the rhythm was only half switched.
    }

    private void loadYaml(String yaml) {
        selectedDefinition = null; selectedRhythmId = null; dialectYaml = null;
        rhythmName.setText("Rytmus"); rhythmName.setToolTipText(null);
        mixer.reset();
        loadingYaml = true;
        try {
            editor.setText(yaml); editor.setCaretPosition(0); editor.discardAllEdits();
            if (yaml.isBlank()) {
                dialectControls.setDialect(picosoft.javafro.base.Dialect.straight(4));
                play.setEnabled(false);
                setEditorStatus("Otvor YAML alebo ho presuň do okna", false);
                return;
            }
            Rhythm rhythm = parseRhythm(yaml, config.initialTempo());
            var folds = editor.getFoldManager();
            folds.reparse();
            for (int i = 0; i < folds.getFoldCount(); i++) folds.getFold(i).setCollapsed(true);
            syncRhythm(rhythm);
        } catch (IllegalArgumentException exception) {
            play.setEnabled(!yaml.isBlank()); setEditorStatus("Neplatný YAML · text môžeš upraviť alebo uložiť", true);
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
        updateFileName();
    }

    private void scheduleDraft() {
        if (!loadingYaml && draftStore != null && savedSource != null) draftTimer.restart();
    }

    /** Tempo or dialect changed from the selected rhythm's YAML values (not yet written into the text). */
    private boolean controlsPending() {
        return selectedDefinition != null && editor.getText().equals(dialectYaml)
                && (tempo.getValue() != selectedDefinition.bpm() || !dialectControls.value().equals(selectedDefinition.dialect()));
    }

    Draft snapshotDraft() {
        DesktopDraftStore.Controls controls = controlsPending()
                ? new DesktopDraftStore.Controls(selectedDefinition.name(), tempo.getValue(), dialectControls.value().offsets())
                : null;
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
                } catch (IllegalArgumentException exception) { setEditorStatus("Neplatný YAML · text môžeš upraviť alebo uložiť", true); }
            }
            editor.setCaretPosition(draft.caret());
        } finally { loadingYaml = false; }
        updateFileName();
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
        updateFileName();
    }

    public boolean requestClose() {
        if (!confirmDocumentChange()) return false;
        flushDraft(); stopPlayback(); return true;
    }

    public void flushDraft() { draftTimer.stop(); persistDraft(); }

    /** Puts the caret into the editor, e.g. right after the window opens. */
    public void focusEditor() { editor.requestFocusInWindow(); }

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
