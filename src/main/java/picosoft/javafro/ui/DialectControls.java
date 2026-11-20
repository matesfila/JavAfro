package picosoft.javafro.ui;

import picosoft.javafro.base.Dialect;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** One independent position control for each pulse, stacked like the web player's dialect panel. */
public final class DialectControls extends JPanel {
    private final JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
    private final List<JSlider> sliders = new ArrayList<>();
    private final List<JLabel> values = new ArrayList<>();
    private final Consumer<Dialect> onChange;
    private Dialect value;
    private boolean updating;

    public DialectControls(Consumer<Dialect> onChange) {
        this(onChange, true);
    }

    public DialectControls(Consumer<Dialect> onChange, boolean showTitle) {
        super(new BorderLayout(0, 4));
        setOpaque(false);
        this.onChange = onChange;
        JButton reset = new JButton("Vynulovať");
        reset.setName("dialectReset");
        reset.setToolTipText("Nastaviť všetky pulzy na presnú pozíciu (0)");
        reset.putClientProperty("JButton.buttonType", "borderless");
        reset.setForeground(new Color(0x816447));
        reset.setFont(reset.getFont().deriveFont(reset.getFont().getSize2D() - 1f));
        reset.addActionListener(event -> reset());
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        if (showTitle) {
            JLabel title = new JLabel("Dialect");
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            toolbar.add(title, BorderLayout.WEST);
        }
        toolbar.add(reset, BorderLayout.EAST);
        rows.setOpaque(false);
        add(toolbar, BorderLayout.NORTH);
        add(rows, BorderLayout.CENTER);
    }

    public Dialect value() { return value; }

    public void setDialect(Dialect dialect) {
        value = dialect;
        updating = true;
        rows.removeAll(); sliders.clear(); values.clear();
        for (int i = 0; i < dialect.offsets().size(); i++) {
            final int index = i;
            JLabel label = new JLabel("Pulz " + (i + 1));
            JLabel offset = new JLabel("", SwingConstants.RIGHT);
            offset.setFont(new Font(Font.MONOSPACED, Font.PLAIN, label.getFont().getSize()));
            offset.setForeground(new Color(0x795739));
            JSlider slider = new JSlider(-100, 100, (int) Math.round(dialect.offsets().get(i) * 100));
            slider.setName("dialectPulse" + (i + 1));
            TouchSliders.install(slider);
            slider.setToolTipText("−1: skôr o polovicu kroku; 0: presne; +1: neskôr o polovicu kroku.");
            label.setLabelFor(slider);
            slider.getAccessibleContext().setAccessibleName("Dialect pozícia pulzu " + (i + 1));
            slider.addChangeListener(event -> changeGap(index));
            sliders.add(slider); values.add(offset);
            JPanel heading = new JPanel(new BorderLayout(8, 0));
            heading.setOpaque(false);
            heading.add(label, BorderLayout.WEST); heading.add(offset, BorderLayout.EAST);
            JPanel cell = new JPanel(new BorderLayout());
            cell.setOpaque(false);
            cell.add(heading, BorderLayout.NORTH); cell.add(slider, BorderLayout.CENTER);
            rows.add(cell);
        }
        refresh(); updating = false;
        revalidate(); repaint();
    }

    /** Every pulse back to its exact grid position; notifies like a slider change. */
    public void reset() {
        if (value == null || value.offsets().stream().allMatch(offset -> offset == 0)) return;
        value = Dialect.straight(value.offsets().size());
        updating = true;
        for (JSlider slider : sliders) slider.setValue(0);
        refresh(); updating = false;
        onChange.accept(value);
    }

    private void changeGap(int index) {
        if (updating) return;
        var gaps = new ArrayList<>(value.offsets());
        gaps.set(index, sliders.get(index).getValue() / 100.0);
        value = Dialect.of(value.offsets().size(), gaps);
        updating = true; refresh(); updating = false;
        onChange.accept(value);
    }

    private void refresh() {
        for (int i = 0; i < values.size(); i++) {
            values.get(i).setText(String.format(Locale.ROOT, "%+.2f", value.offsets().get(i)));
        }
    }
}
