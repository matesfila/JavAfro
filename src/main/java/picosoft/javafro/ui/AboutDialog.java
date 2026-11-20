package picosoft.javafro.ui;

import picosoft.javafro.AppInfo;
import javax.swing.*;
import java.awt.*;

/** "O aplikácii" like web AboutDialog.tsx; without the update button, which belongs to the offline web app. */
final class AboutDialog {
    private AboutDialog() { }

    static void show(Component owner) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "O aplikácii", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(content(dialog::dispose));
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    static JPanel content(Runnable close) {
        JPanel panel = new JPanel();
        panel.setName("aboutContent");
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SandTheme.PAPER);
        panel.setBorder(BorderFactory.createEmptyBorder(26, 28, 22, 28));
        JLabel brand = new JLabel("JavAfro", UiIcons.logo(36), SwingConstants.LEFT);
        brand.setIconTextGap(10);
        brand.setFont(SandTheme.heading(brand.getFont(), 28f));
        add(panel, brand, 0);
        JLabel version = new JLabel("Verzia " + AppInfo.version());
        version.setForeground(SandTheme.MUTED);
        add(panel, version, 6);
        add(panel, term("Autor"), 22);
        add(panel, value(AppInfo.creator()), 4);
        add(panel, term("Pomocník pri vývoji"), 16);
        add(panel, value(AppInfo.assistant()), 4);
        JLabel samples = new JLabel("Použité sample");
        samples.setFont(SandTheme.heading(samples.getFont(), samples.getFont().getSize2D() + 1f));
        add(panel, samples, 24);
        add(panel, paragraph("Basové bubny (dundun, sangban, kensereni) nahrávané v skúšobni Aneboafro v Prahe v roku 2011. Nahral: "
                + AppInfo.creator()), 6);
        add(panel, paragraph("Sample djembe nahrané počas Kon workshopu Tomáša Mareka v Prahe. Na djembe hral Tomáš Marek, nahral "
                + AppInfo.creator()), 8);
        JButton closeButton = new JButton("Zavrieť");
        closeButton.setName("aboutClose");
        closeButton.addActionListener(event -> close.run());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        actions.add(closeButton);
        add(panel, actions, 20);
        return panel;
    }

    private static void add(JPanel panel, JComponent component, int gap) {
        if (gap > 0) panel.add(Box.createVerticalStrut(gap));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(component);
    }

    private static JLabel term(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(SandTheme.MUTED);
        return label;
    }

    private static JLabel value(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 3f));
        return label;
    }

    /** Wrapped paragraph; HTML width makes the label report the wrapped height to BoxLayout. */
    private static JLabel paragraph(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return new JLabel("<html><div style='width:340px'>" + escaped + "</div></html>");
    }
}
