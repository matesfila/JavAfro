package picosoft.javafro.ui;

import javax.swing.*;
import java.awt.*;

/** Keyboard-accessible disclosure that keeps the same controls when collapsed. */
final class DisclosurePanel extends JPanel {
    DisclosurePanel(String title, String name, JComponent content) {
        super(new BorderLayout(0, 6));
        JToggleButton toggle = new JToggleButton("▸ " + title);
        toggle.setName(name);
        toggle.setFont(toggle.getFont().deriveFont(Font.BOLD, 16f));
        toggle.getAccessibleContext().setAccessibleName(title);
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.setContentAreaFilled(false);
        toggle.setBorderPainted(false);
        toggle.setMargin(new Insets(6, 0, 6, 0));
        JPanel body = new JPanel(new BorderLayout());
        body.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 0));
        body.add(content, BorderLayout.CENTER);
        body.setVisible(false);
        toggle.addActionListener(event -> {
            boolean expanded = toggle.isSelected();
            toggle.setText((expanded ? "▾ " : "▸ ") + title);
            body.setVisible(expanded);
            revalidate();
            repaint();
        });
        add(toggle, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }
}
