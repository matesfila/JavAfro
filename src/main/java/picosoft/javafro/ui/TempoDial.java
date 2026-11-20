package picosoft.javafro.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.MouseEvent;

/** Slider model and keyboard accessibility, with a relative-drag rotary face. */
final class TempoDial extends JSlider {
    TempoDial(int minimum, int maximum, int value) {
        super(minimum, maximum, value);
        setPreferredSize(new Dimension(96, 96));
        setMinimumSize(new Dimension(64, 64));
        setOpaque(false);
        setToolTipText("Ťahaj hore pre zrýchlenie, dole pre spomalenie");
        getAccessibleContext().setAccessibleName("Tempo v BPM");
    }

    @Override public void updateUI() { setUI(new DialUI(this)); }

    private static final class DialUI extends BasicSliderUI {
        DialUI(JSlider slider) { super(slider); }
        @Override protected TrackListener createTrackListener(JSlider slider) {
            return new TrackListener() {
                private int startY, startValue;
                @Override public void mousePressed(MouseEvent event) {
                    if (!slider.isEnabled() || !SwingUtilities.isLeftMouseButton(event)) return;
                    slider.requestFocusInWindow();
                    startY = event.getY(); startValue = slider.getValue();
                    slider.setValueIsAdjusting(true);
                }
                @Override public void mouseDragged(MouseEvent event) {
                    if (slider.getValueIsAdjusting()) slider.setValue(startValue + Math.round((startY - event.getY()) / 4f));
                }
                @Override public void mouseReleased(MouseEvent event) { slider.setValueIsAdjusting(false); }
            };
        }
        @Override public void paint(Graphics graphics, JComponent component) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Math.min(component.getWidth(), component.getHeight()) - 8;
                int x = (component.getWidth() - size) / 2, y = (component.getHeight() - size) / 2;
                g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double ratio = (slider.getValue() - (double) slider.getMinimum()) / Math.max(1, slider.getMaximum() - slider.getMinimum());
                g.setColor(SandTheme.TRACK); g.drawArc(x + 2, y + 2, size - 4, size - 4, -45, 270);
                g.setColor(SandTheme.SLIDER);
                g.drawArc(x + 2, y + 2, size - 4, size - 4, 225, -(int) Math.round(270 * ratio));
                int inset = 10;
                boolean dragging = slider.getValueIsAdjusting();
                g.setColor(dragging ? new Color(0xDFC8AA) : SandTheme.KEY);
                g.fillOval(x + inset, y + inset, size - 2 * inset, size - 2 * inset);
                g.setColor(SandTheme.SLIDER); g.setStroke(new BasicStroke(2));
                g.drawOval(x + inset, y + inset, size - 2 * inset, size - 2 * inset);
                double angle = Math.toRadians(-225 + 270 * ratio);
                double cx = x + size / 2.0, cy = y + size / 2.0;
                g.setColor(SandTheme.ACCENT);
                g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine((int) (cx + Math.cos(angle) * size * .16), (int) (cy + Math.sin(angle) * size * .16),
                        (int) (cx + Math.cos(angle) * size * .30), (int) (cy + Math.sin(angle) * size * .30));
                if (slider.hasFocus()) { g.setColor(new Color(0xB88A5B)); g.setStroke(new BasicStroke(2)); g.drawOval(x, y, size, size); }
            } finally { g.dispose(); }
        }
    }

    static JPanel controls(JSlider dial) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        JLabel label = new JLabel("Tempo"); label.setLabelFor(dial);
        label.setFont(SandTheme.heading(label.getFont(), 16f));
        JButton minus = step("−", "Spomaliť o 1 BPM", () -> dial.setValue(Math.max(1, dial.getValue() - 1)), dial);
        JButton plus = step("+", "Zrýchliť o 1 BPM", () -> {
            int value = dial.getValue() + 1;
            dial.setMaximum(Math.max(dial.getMaximum(), value)); dial.setValue(value);
        }, dial);
        JSpinner number = new JSpinner(new SpinnerNumberModel(dial.getValue(), 1, Integer.MAX_VALUE, 1));
        number.setName("tempoNumber"); number.setPreferredSize(new Dimension(96, 40));
        JFormattedTextField field = ((JSpinner.DefaultEditor) number.getEditor()).getTextField();
        field.setFont(number.getFont().deriveFont(Font.BOLD, 20f));
        field.setForeground(new Color(0x795739));
        var formatter = ((JSpinner.DefaultEditor) number.getEditor()).getTextField().getFormatter();
        if (formatter instanceof javax.swing.text.DefaultFormatter valueFormatter) valueFormatter.setCommitsOnValidEdit(true);
        number.getAccessibleContext().setAccessibleName("Presné tempo v BPM");
        number.addChangeListener(event -> {
            int value = (Integer) number.getValue();
            dial.setMinimum(Math.min(dial.getMinimum(), value));
            dial.setMaximum(Math.max(dial.getMaximum(), value));
            dial.setValue(value);
        });
        dial.addChangeListener(event -> {
            number.setValue(dial.getValue());
            minus.setEnabled(dial.getValue() > 1);
            plus.setEnabled(dial.getValue() < Integer.MAX_VALUE);
        });
        minus.setEnabled(dial.getValue() > 1); plus.setEnabled(dial.getValue() < Integer.MAX_VALUE);
        JLabel unit = new JLabel("BPM");
        unit.setForeground(SandTheme.MUTED);
        JPanel value = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        value.setOpaque(false);
        value.add(number); value.add(unit);
        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.setOpaque(false);
        heading.add(label, BorderLayout.WEST);
        heading.add(value, BorderLayout.EAST);
        JPanel knob = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        knob.setOpaque(false);
        knob.add(minus); knob.add(dial); knob.add(plus);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(knob, BorderLayout.CENTER);
        return panel;
    }

    private static JButton step(String text, String name, Runnable action, JSlider dial) {
        JButton button = SandTheme.key(new JButton(text)); button.setPreferredSize(new Dimension(44, 40));
        button.setToolTipText(name);
        button.setFocusable(false); button.getAccessibleContext().setAccessibleName(name);
        button.addActionListener(event -> {
            if (text.equals("−")) dial.setMinimum(Math.min(dial.getMinimum(), Math.max(1, dial.getValue() - 1)));
            action.run();
        });
        return button;
    }
}
