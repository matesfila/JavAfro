package picosoft.javafro.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/** Keyboard-accessible disclosure (web {@code <details>} section) that keeps the same controls when collapsed. */
final class DisclosurePanel extends JPanel {
    private final JToggleButton toggle;
    private final JPanel body = new JPanel(new BorderLayout());

    DisclosurePanel(String title, String name, JComponent content) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SandTheme.BORDER),
                BorderFactory.createEmptyBorder(6, 0, 0, 0)));
        toggle = new JToggleButton(title, new Chevron(false));
        toggle.setSelectedIcon(new Chevron(true));
        toggle.setName(name);
        toggle.setFont(SandTheme.heading(toggle.getFont(), 17f));
        toggle.setForeground(SandTheme.INK);
        toggle.getAccessibleContext().setAccessibleName(title);
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.setIconTextGap(8);
        toggle.setContentAreaFilled(false);
        toggle.setBorderPainted(false);
        toggle.setOpaque(false);
        toggle.setMargin(new Insets(6, 0, 6, 0));
        toggle.putClientProperty("JButton.buttonType", "borderless");
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));
        body.add(content, BorderLayout.CENTER);
        body.setVisible(false);
        toggle.addActionListener(event -> {
            body.setVisible(toggle.isSelected());
            revalidate();
            repaint();
        });
        add(toggle, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    void setExpanded(boolean expanded) {
        if (toggle.isSelected() != expanded) toggle.doClick(0);
    }

    boolean isExpanded() { return toggle.isSelected(); }

    /** Chevron in the heading color: right when collapsed, down when expanded. */
    private record Chevron(boolean expanded) implements Icon {
        @Override public int getIconWidth() { return 14; }
        @Override public int getIconHeight() { return 14; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(SandTheme.LABEL);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D path = new Path2D.Double();
                if (expanded) { path.moveTo(3, 5); path.lineTo(7, 9); path.lineTo(11, 5); }
                else { path.moveTo(5, 3); path.lineTo(9, 7); path.lineTo(5, 11); }
                g.draw(path);
            } finally { g.dispose(); }
        }
    }
}
