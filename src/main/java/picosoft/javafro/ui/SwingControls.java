package picosoft.javafro.ui;

import picosoft.javafro.base.Swing;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** One independent position control for each pulse. */
public final class SwingControls extends JPanel {
    private final JPanel row = new JPanel(new GridLayout(1, 0, 12, 0));
    private final List<JSlider> sliders = new ArrayList<>();
    private final List<JLabel> labels = new ArrayList<>();
    private final Consumer<Swing> onChange;
    private Swing value;
    private boolean updating;

    public SwingControls(Consumer<Swing> onChange) {
        super(new BorderLayout(12, 0));
        this.onChange = onChange;
        JLabel title = new JLabel("Swing");
        title.setPreferredSize(new Dimension(110, 24));
        add(title, BorderLayout.WEST);
        JScrollPane scroll = new JScrollPane(row, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(500, 78));
        add(scroll, BorderLayout.CENTER);
    }

    public Swing value() { return value; }

    public void setSwing(Swing swing) {
        value = swing;
        updating = true;
        row.removeAll(); sliders.clear(); labels.clear();
        for (int i = 0; i < swing.offsets().size(); i++) {
            final int index = i;
            JPanel cell = new JPanel(new BorderLayout());
            cell.setPreferredSize(new Dimension(160, 54));
            JLabel label = new JLabel();
            JSlider slider = new JSlider(-100, 100, (int) Math.round(swing.offsets().get(i) * 100));
            slider.setName("swingPulse" + (i + 1));
            slider.setMajorTickSpacing(100);
            slider.setPaintTicks(true);
            slider.setToolTipText("−1: skôr o polovicu kroku; 0: presne; +1: neskôr o polovicu kroku.");
            label.setLabelFor(slider);
            slider.getAccessibleContext().setAccessibleName("Swing pozícia pulzu " + (i + 1));
            slider.addChangeListener(event -> changeGap(index));
            sliders.add(slider); labels.add(label);
            cell.add(label, BorderLayout.NORTH); cell.add(slider, BorderLayout.CENTER);
            row.add(cell);
        }
        refresh(); updating = false;
        revalidate(); repaint();
    }

    private void changeGap(int index) {
        if (updating) return;
        var gaps = new ArrayList<>(value.offsets());
        gaps.set(index, sliders.get(index).getValue() / 100.0);
        value = Swing.of(value.offsets().size(), gaps);
        updating = true; refresh(); updating = false;
        onChange.accept(value);
    }

    private void refresh() {
        int count = value.offsets().size();
        for (int i = 0; i < sliders.size(); i++) {
            labels.get(i).setText(String.format(Locale.ROOT, "%d. pulz: %+.2f", i + 1, value.offsets().get(i)));
        }
    }
}
