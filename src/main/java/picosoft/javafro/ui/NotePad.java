package picosoft.javafro.ui;

import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.TextMode;
import picosoft.javafro.io.RhythmLoader;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * On-screen note keyboard for the track under the caret (port of web NotePad.tsx and note-input.ts).
 * Buttons never take focus, so the caret and selection stay in the editor; every key press is one undo step.
 */
final class NotePad extends JPanel {
    private static final String WORD = "[a-zA-Z0-9.]";
    private final RTextArea editor;
    private final JLabel instrumentLabel = new JLabel("Vyber riadok nástroja");
    private final JPanel noteKeys = new JPanel(new GridLayout(0, 8, 5, 5));
    private String instrument;

    NotePad(RTextArea editor) {
        super(new BorderLayout(0, 6));
        this.editor = editor;
        setName("notePad");
        setBackground(SandTheme.PAPER);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SandTheme.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 8, 8)));
        getAccessibleContext().setAccessibleName("Zadávanie nôt");
        instrumentLabel.setName("noteInstrument");
        instrumentLabel.setForeground(SandTheme.LABEL);
        noteKeys.setOpaque(false);
        noteKeys.getAccessibleContext().setAccessibleName("Noty nástroja");
        JPanel navigation = new JPanel(new GridLayout(1, 0, 5, 0));
        navigation.setOpaque(false);
        navigation.add(key("⌫", "Vymazať pred kurzorom", "noteBackspace", () -> command(DefaultEditorKit.deletePrevCharAction)));
        navigation.add(key("␣", "Medzera", "noteSpace", () -> insert(" ")));
        navigation.add(key("↵", "Nový riadok (Enter)", "noteEnter", () -> command(DefaultEditorKit.insertBreakAction)));
        navigation.add(key(".", "Pauza (.)", "noteRest", () -> insert(".")));
        navigation.add(key("|", "Oddeľovač taktov (|)", "noteBar", () -> insert("|")));
        navigation.add(key("←", "Kurzor doľava", "noteLeft", () -> command(DefaultEditorKit.backwardAction)));
        navigation.add(key("↑", "Kurzor hore", "noteUp", () -> command(DefaultEditorKit.upAction)));
        navigation.add(key("↓", "Kurzor dole", "noteDown", () -> command(DefaultEditorKit.downAction)));
        navigation.add(key("→", "Kurzor doprava", "noteRight", () -> command(DefaultEditorKit.forwardAction)));
        add(instrumentLabel, BorderLayout.NORTH);
        add(noteKeys, BorderLayout.CENTER);
        add(navigation, BorderLayout.SOUTH);
    }

    String instrument() { return instrument; }

    /** Shows the notes of {@code id} (a track name), or a hint when the caret is outside a track. */
    void setInstrument(String id) {
        if (java.util.Objects.equals(id, instrument) && noteKeys.getComponentCount() > 0 == (id != null)) return;
        instrument = id;
        instrumentLabel.setText(id == null ? "Vyber riadok nástroja" : id);
        noteKeys.removeAll();
        List<String> notes = RhythmLoader.notesFor(id);
        for (String note : notes) noteKeys.add(key(note, "Vložiť " + note, "note-" + note, () -> insert(note)));
        noteKeys.revalidate(); noteKeys.repaint();
    }

    /** Insert like typing; balafon's multi-character notes are space-separated tokens. */
    void insert(String text) {
        if (!editor.isEditable()) return;
        editor.beginAtomicEdit();
        try {
            if ("balafon".equals(instrument) && !text.isBlank()) insertToken(text);
            else insertText(text);
        } catch (BadLocationException exception) {
            UIManager.getLookAndFeel().provideErrorFeedback(editor);
        } finally {
            editor.endAtomicEdit();
        }
        editor.requestFocusInWindow();
    }

    private void insertToken(String text) throws BadLocationException {
        String doc = editor.getText();
        int from = editor.getSelectionStart(), to = editor.getSelectionEnd();
        if (from == to && editor.getTextMode() == TextMode.OVERWRITE && !text.equals("|")) {
            while (from > 0 && word(doc.charAt(from - 1))) from--;
            while (to < doc.length() && word(doc.charAt(to))) to++;
            while (to < doc.length() && (doc.charAt(to) == ' ' || doc.charAt(to) == '\t')) to++;
        }
        String insert = (from > 0 && word(doc.charAt(from - 1)) ? " " : "") + text + " ";
        editor.replaceRange(insert, from, to);
        editor.setCaretPosition(from + insert.length());
    }

    private void insertText(String text) throws BadLocationException {
        int start = editor.getSelectionStart(), end = editor.getSelectionEnd();
        if (start == end && editor.getTextMode() == TextMode.OVERWRITE) {
            // Replace characters within the line only; the line break is never overwritten.
            Element line = editor.getDocument().getDefaultRootElement()
                    .getElement(editor.getDocument().getDefaultRootElement().getElementIndex(start));
            int lineEnd = line.getEndOffset() - 1;
            String rest = editor.getText(start, Math.max(0, lineEnd - start));
            int count = text.codePointCount(0, text.length());
            end = start + (rest.isEmpty() ? 0 : rest.offsetByCodePoints(0, Math.min(count, rest.codePointCount(0, rest.length()))));
        }
        editor.replaceRange(text, start, end);
        editor.setCaretPosition(start + text.length());
    }

    private void command(String name) {
        Action action = editor.getActionMap().get(name);
        if (action == null || !editor.isEditable() && name.equals(DefaultEditorKit.deletePrevCharAction)) return;
        editor.beginAtomicEdit();
        try { action.actionPerformed(new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, name)); }
        finally { editor.endAtomicEdit(); }
        editor.requestFocusInWindow();
    }

    private static boolean word(char character) { return String.valueOf(character).matches(WORD); }

    private static JButton key(String label, String title, String name, Runnable action) {
        JButton button = SandTheme.key(new JButton(label));
        button.setName(name);
        button.setToolTipText(title);
        button.getAccessibleContext().setAccessibleName(title);
        button.setFocusable(false);
        button.setMargin(new Insets(6, 4, 6, 4));
        button.setFont(button.getFont().deriveFont(Font.BOLD, button.getFont().getSize2D() + 2f));
        button.addActionListener(event -> action.run());
        return button;
    }
}
