package picosoft.javafro.ui;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Editor messages (YAML errors, „Upravené · stlač Play“, the draft state) in a fixed slot of the header
 * (web HeaderStatus). The preferred size never depends on the text, so a message never changes the layout.
 * Long text is clipped to two lines with an ellipsis; a click opens the whole message below the slot.
 */
final class HeaderStatus extends JComponent {
    /** Independent message sources, shown in this order. */
    enum Slot { EDITOR, DRAFT }

    static final int MAX_LINES = 2;
    private static final int PAD_X = 10;
    private static final int PAD_Y = 4;
    private static final int POPUP_MAX_HEIGHT = 320;
    private static final Color POPUP_ERROR_BORDER = new Color(0xE8C3B4);

    private record Message(String text, boolean invalid) {}
    /** One painted line; {@code invalid} picks the error color of the message it comes from. */
    record Line(String text, boolean invalid) {}

    private final Message[] messages = new Message[Slot.values().length];
    private final JPopupMenu popup = new JPopupMenu();
    private final JTextArea full = new JTextArea();
    private final JScrollPane fullScroll = new JScrollPane(full);
    private long popupClosedAt;

    HeaderStatus() {
        setName("headerStatus");
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.DIALOG, Font.PLAIN, 12);
        setFont(base.deriveFont(Font.PLAIN, Math.max(11f, base.getSize2D() - 2f)));
        setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        java.util.Arrays.fill(messages, new Message("", false));

        full.setName("headerStatusFull");
        full.setEditable(false);
        full.setLineWrap(true);
        full.setWrapStyleWord(true);
        full.setFocusable(false); // keep the caret in the editor
        full.setFont(getFont());
        full.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        fullScroll.setBorder(BorderFactory.createEmptyBorder());
        popup.setName("headerStatusPopup");
        popup.setLayout(new BorderLayout());
        popup.add(fullScroll, BorderLayout.CENTER);
        popup.setFocusable(false);
        // Focus stays in the editor, so the popup's own key bindings never see Escape; catch it while open.
        KeyEventDispatcher escape = event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED || event.getKeyCode() != KeyEvent.VK_ESCAPE || !popup.isVisible()) return false;
            popup.setVisible(false);
            return true;
        };
        popup.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escape);
            }
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                popupClosedAt = System.currentTimeMillis();
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escape);
            }
            public void popupMenuCanceled(PopupMenuEvent event) { }
        });
        full.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { popup.setVisible(false); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || text().isEmpty()) return;
                // The click that closed the open popup must not reopen it right away.
                if (System.currentTimeMillis() - popupClosedAt < 250) return;
                showFullMessage();
            }
        });
    }

    /** Replaces one message; an empty text clears it. A changed message closes the opened full text. */
    void setMessage(Slot slot, String text, boolean invalid) {
        Message next = new Message(text == null ? "" : text.strip(), invalid && text != null && !text.isBlank());
        if (next.equals(messages[slot.ordinal()])) return;
        messages[slot.ordinal()] = next;
        popup.setVisible(false);
        String all = text();
        setCursor(all.isEmpty() ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(all.isEmpty() ? null : "Zobraziť celú správu");
        repaint(); // never revalidate: the slot keeps its size
    }

    String message(Slot slot) { return messages[slot.ordinal()].text(); }

    /** All non-empty messages, one per line. */
    String text() {
        List<String> parts = new ArrayList<>();
        for (Message message : messages) if (!message.text().isEmpty()) parts.add(message.text());
        return String.join("\n", parts);
    }

    boolean isInvalid() {
        for (Message message : messages) if (message.invalid()) return true;
        return false;
    }

    boolean isFullMessageShown() { return popup.isVisible(); }

    /** The whole message below the slot; closed by a click elsewhere, Escape or a new message. */
    void showFullMessage() {
        if (!isShowing() || text().isEmpty()) return;
        boolean invalid = isInvalid();
        full.setText(text());
        full.setCaretPosition(0);
        full.setForeground(invalid ? SandTheme.ERROR : SandTheme.INK);
        full.setBackground(invalid ? SandTheme.ERROR_BACKGROUND : SandTheme.PAPER);
        fullScroll.getViewport().setBackground(full.getBackground());
        popup.setBorder(BorderFactory.createLineBorder(invalid ? POPUP_ERROR_BORDER : SandTheme.BORDER));
        int width = Math.max(240, getWidth());
        full.setSize(width - 2, Short.MAX_VALUE);
        int height = Math.min(POPUP_MAX_HEIGHT, full.getPreferredSize().height) + 2;
        popup.setPopupSize(width, height);
        popup.show(this, (getWidth() - width) / 2, getHeight());
    }

    /** Constant: two lines of text plus padding, whatever the message. Zero width, so the slot takes the free space. */
    @Override public Dimension getPreferredSize() {
        Insets insets = getInsets();
        FontMetrics metrics = getFontMetrics(getFont());
        return new Dimension(0, insets.top + insets.bottom + 2 * PAD_Y + MAX_LINES * metrics.getHeight());
    }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    /** The lines painted at the current width (tests and painting). */
    List<Line> visibleLines() {
        Insets insets = getInsets();
        return layout(getFontMetrics(getFont()), getWidth() - insets.left - insets.right - 2 * PAD_X);
    }

    private List<Line> layout(FontMetrics metrics, int width) {
        List<Line> lines = new ArrayList<>();
        boolean more = false;
        for (Message message : messages) {
            for (String line : wrap(message.text(), metrics, width)) {
                if (lines.size() == MAX_LINES) { more = true; break; }
                lines.add(new Line(line, message.invalid()));
            }
        }
        if (more) {
            Line last = lines.removeLast();
            lines.add(new Line(ellipsis(last.text(), metrics, width), last.invalid()));
        }
        return lines;
    }

    /** Greedy word wrap; a word wider than the line is broken anywhere (web overflow-wrap:anywhere). */
    static List<String> wrap(String text, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>();
        int available = Math.max(1, width);
        for (String paragraph : text.split("\n")) {
            String rest = paragraph.strip();
            while (!rest.isEmpty()) {
                if (metrics.stringWidth(rest) <= available) { lines.add(rest); break; }
                int fit = 0, used = 0;
                while (fit < rest.length() && used + metrics.charWidth(rest.charAt(fit)) <= available) used += metrics.charWidth(rest.charAt(fit++));
                fit = Math.max(1, fit);
                int space = rest.lastIndexOf(' ', fit);
                int end = space > 0 ? space : fit;
                lines.add(rest.substring(0, end).stripTrailing());
                rest = rest.substring(end).stripLeading();
            }
        }
        return lines;
    }

    private static String ellipsis(String line, FontMetrics metrics, int width) {
        String text = line;
        while (!text.isEmpty() && metrics.stringWidth(text + "…") > width) text = text.substring(0, text.length() - 1);
        return text.stripTrailing() + "…";
    }

    @Override protected void paintComponent(Graphics graphics) {
        List<Line> lines = visibleLines();
        if (lines.isEmpty()) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Object textHints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
            if (textHints instanceof java.util.Map<?, ?> hints) g.addRenderingHints(hints);
            else g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            Insets insets = getInsets();
            int innerWidth = getWidth() - insets.left - insets.right;
            int innerHeight = getHeight() - insets.top - insets.bottom;
            int textWidth = 0;
            for (Line line : lines) textWidth = Math.max(textWidth, metrics.stringWidth(line.text()));
            int boxWidth = Math.min(innerWidth, textWidth + 2 * PAD_X);
            int boxHeight = lines.size() * metrics.getHeight() + 2 * PAD_Y;
            int x = insets.left + (innerWidth - boxWidth) / 2;
            int y = insets.top + (innerHeight - boxHeight) / 2;
            if (isInvalid()) {
                g.setColor(SandTheme.ERROR_BACKGROUND);
                g.fillRoundRect(x, y, boxWidth, boxHeight, 8, 8);
            }
            g.clipRect(insets.left, insets.top, innerWidth, innerHeight);
            int baseline = y + PAD_Y + metrics.getAscent();
            for (Line line : lines) {
                g.setColor(line.invalid() ? SandTheme.ERROR : SandTheme.MUTED);
                g.drawString(line.text(), x + PAD_X, baseline);
                baseline += metrics.getHeight();
            }
        } finally { g.dispose(); }
    }
}
