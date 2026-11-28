package picosoft.javafro.ui;

import org.fife.ui.rsyntaxtextarea.SquiggleUnderlineHighlightPainter;
import picosoft.javafro.io.RhythmLoader;

import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Wavy underline of the faulty line and the rhythm name of each broken rhythm (port of web
 * {@code rhythm-diagnostics.ts}). No message is shown: the error appears only when the rhythm is played.
 * Recomputed shortly after typing stops.
 */
final class RhythmErrorHighlighter {
    static final Color COLOR = new Color(0xa2, 0x3f, 0x2d);
    private final JTextComponent editor;
    private final IntSupplier defaultBpm;
    private final Highlighter.HighlightPainter painter = new SquiggleUnderlineHighlightPainter(COLOR);
    private final List<Object> highlights = new ArrayList<>();
    private final Timer timer;

    RhythmErrorHighlighter(JTextComponent editor, IntSupplier defaultBpm) {
        this.editor = editor;
        this.defaultBpm = defaultBpm;
        timer = new Timer(150, event -> refresh());
        timer.setRepeats(false);
        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { timer.restart(); }
            public void removeUpdate(DocumentEvent event) { timer.restart(); }
            public void changedUpdate(DocumentEvent event) { }
        });
    }

    /** Replaces the underlines with those of the current text at once. */
    void refresh() {
        timer.stop();
        Highlighter highlighter = editor.getHighlighter();
        for (Object highlight : highlights) highlighter.removeHighlight(highlight);
        highlights.clear();
        String text = editor.getText();
        for (int[] range : ranges(text, defaultBpm.getAsInt())) {
            try { highlights.add(highlighter.addHighlight(range[0], range[1], painter)); }
            catch (BadLocationException exception) { /* the text changed meanwhile; the timer runs again */ }
        }
    }

    /** Underlined [start, end) ranges, in the order they appear; for tests. */
    List<int[]> highlighted() {
        List<int[]> ranges = new ArrayList<>();
        for (Highlighter.Highlight highlight : editor.getHighlighter().getHighlights()) {
            if (highlight.getPainter() == painter) ranges.add(new int[] {highlight.getStartOffset(), highlight.getEndOffset()});
        }
        ranges.sort((a, b) -> Integer.compare(a[0], b[0]));
        return ranges;
    }

    static List<int[]> ranges(String text, int defaultBpm) {
        List<int[]> ranges = new ArrayList<>();
        for (var entry : RhythmLoader.entries(text, defaultBpm)) {
            for (int[] range : entry.ranges()) {
                if (range[1] > range[0] && ranges.stream().noneMatch(other -> other[0] == range[0])) ranges.add(range);
            }
        }
        return ranges;
    }
}
