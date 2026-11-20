package picosoft.javafro.ui;

import picosoft.javafro.base.Rhythm;
import picosoft.javafro.base.AudioConstants;
import picosoft.javafro.sequencer.PlaybackSettings;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Mixer state survives Stop/Play and belongs to the selected rhythm's tracks.
 * Each row is narrow like the web {@code .mixer-track}: name with Mute/Solo, then the volume slider with its value.
 */
final class MixerPanel extends JPanel {
    private final List<JSlider> volumes = new ArrayList<>();
    private final List<JToggleButton> mutes = new ArrayList<>();
    private final List<JToggleButton> solos = new ArrayList<>();
    private List<String> signature = List.of();
    private PlaybackSettings settings;
    private final int initialVolume;
    private final JSlider master;
    private final JPanel masterRow = new JPanel(new GridBagLayout());

    MixerPanel(int initialVolume) { this(initialVolume, (int) (AudioConstants.OUTPUT_GAIN * 100)); }

    MixerPanel(int initialVolume, int initialMaster) {
        super(new GridLayout(0, 1, 0, 0));
        setOpaque(false);
        this.initialVolume = initialVolume * 2;
        master = new JSlider(0, 100, initialMaster);
        master.setName("masterVolume");
        TouchSliders.install(master);
        master.getAccessibleContext().setAccessibleName("Master hlasitosť");
        JLabel label = new JLabel("Master");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setLabelFor(master);
        JLabel value = valueLabel(master.getValue() + " %");
        layoutRow(masterRow, label, null, master, value, false);
        master.addChangeListener(event -> {
            value.setText(master.getValue() + " %");
            if (settings != null) settings.setMasterVolume(master.getValue());
        });
        add(masterRow);
    }

    void reset() {
        settings = null;
        signature = List.of();
        volumes.clear(); mutes.clear(); solos.clear(); removeAll();
        add(masterRow);
        revalidate(); repaint();
    }

    void setRhythm(Rhythm rhythm) {
        List<String> next = new ArrayList<>();
        next.add(rhythm.name());
        rhythm.tracks().forEach(track -> next.add(track.instrumentId()));
        if (signature.equals(next)) return;
        signature = List.copyOf(next);
        settings = null;
        volumes.clear(); mutes.clear(); solos.clear(); removeAll();
        add(masterRow);
        for (int i = 0; i < rhythm.tracks().size(); i++) {
            final int index = i;
            String instrument = rhythm.tracks().get(i).instrumentId();
            String name = (i + 1) + " · " + instrument;
            JSlider volume = new JSlider(0, 200, initialVolume);
            TouchSliders.install(volume);
            volume.setName("trackVolume-" + i);
            volume.getAccessibleContext().setAccessibleName("Hlasitosť " + name);
            JToggleButton mute = smallToggle("Mute", "trackMute-" + i, "Mute " + name);
            JToggleButton solo = smallToggle("Solo", "trackSolo-" + i, "Solo " + name);
            solo.addActionListener(event -> {
                boolean unsolo = isSoloed(index);
                for (int track = 0; track < mutes.size(); track++) {
                    mutes.get(track).setSelected(!unsolo && track != index);
                    update(track);
                }
            });
            JLabel value = valueLabel(trackVolumeText(initialVolume));
            JLabel label = new JLabel(instrument);
            label.setToolTipText(name);
            label.setLabelFor(volume);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            actions.setOpaque(false);
            actions.add(mute); actions.add(solo);
            JPanel row = new JPanel(new GridBagLayout());
            layoutRow(row, label, actions, volume, value, true);
            volumes.add(volume); mutes.add(mute); solos.add(solo); add(row);
            volume.addChangeListener(event -> { value.setText(trackVolumeText(volume.getValue())); update(index); });
            mute.addActionListener(event -> update(index));
        }
        revalidate(); repaint();
    }

    void bind(PlaybackSettings settings) {
        this.settings = settings;
        settings.setMasterVolume(master.getValue());
        for (int i = 0; i < volumes.size(); i++) update(i);
    }

    /** Prepare the queued mix without rebinding controls away from the sounding rhythm. */
    void configure(PlaybackSettings target, Rhythm rhythm) {
        List<String> next = new ArrayList<>();
        next.add(rhythm.name());
        rhythm.tracks().forEach(track -> next.add(track.instrumentId()));
        target.setMasterVolume(master.getValue());
        boolean same = signature.equals(next);
        for (int i = 0; i < rhythm.tracks().size(); i++) {
            target.setTrackMix(i, same ? volumes.get(i).getValue() : initialVolume,
                    same && mutes.get(i).isSelected());
        }
    }

    /** The label is the row's first child; the slider and its value share the second line. */
    private static void layoutRow(JPanel row, JLabel label, JComponent actions, JSlider slider, JLabel value, boolean separated) {
        row.setOpaque(false);
        row.setBorder(separated
                ? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, SandTheme.BORDER),
                        BorderFactory.createEmptyBorder(8, 0, 2, 0))
                : BorderFactory.createEmptyBorder(4, 0, 6, 0));
        // Columns: 0 = name (grows), 1 = rest of the buttons, 2 = value. The buttons span 1–2, the slider spans 0–1,
        // so the slider gets the full width except the value column.
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = actions == null ? 3 : 1;
        row.add(label, c);
        if (actions != null) {
            c.gridx = 1; c.gridwidth = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.EAST;
            row.add(actions, c);
        }
        c.gridx = 0; c.gridy = 1; c.gridwidth = 2; c.weightx = 0; c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.WEST;
        row.add(slider, c);
        c.gridx = 2; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(0, 6, 0, 0);
        row.add(value, c);
        // GridBag gives a spanning component's extra width to its last column; reserve it in column 1 instead.
        int valueWidth = value.getPreferredSize().width + c.insets.left;
        int actionsWidth = actions == null ? 0 : actions.getPreferredSize().width;
        ((GridBagLayout) row.getLayout()).columnWidths = new int[]{0, Math.max(0, actionsWidth - valueWidth), valueWidth};
    }

    private static JLabel valueLabel(String text) {
        JLabel value = new JLabel(text, SwingConstants.RIGHT);
        value.setForeground(SandTheme.MUTED);
        value.setPreferredSize(new Dimension(52, 24));
        return value;
    }

    private static JToggleButton smallToggle(String text, String name, String accessibleName) {
        JToggleButton button = new JToggleButton(text);
        button.setName(name);
        button.setMargin(new Insets(2, 8, 2, 8));
        button.setFont(button.getFont().deriveFont(button.getFont().getSize2D() - 1f));
        button.getAccessibleContext().setAccessibleName(accessibleName);
        return button;
    }

    /** A track is soloed when it is the only unmuted one (and there is more than one track). */
    private boolean isSoloed(int index) {
        if (mutes.size() < 2 || mutes.get(index).isSelected()) return false;
        for (int i = 0; i < mutes.size(); i++) if (i != index && !mutes.get(i).isSelected()) return false;
        return true;
    }

    private void update(int index) {
        for (int i = 0; i < solos.size(); i++) solos.get(i).setSelected(isSoloed(i));
        if (settings != null) settings.setTrackMix(index, volumes.get(index).getValue(), mutes.get(index).isSelected());
    }

    private static String trackVolumeText(int volume) {
        return (volume % 2 == 0 ? Integer.toString(volume / 2) : Double.toString(volume / 2.0)) + " %";
    }
}
