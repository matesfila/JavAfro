package picosoft.javafro.ui;

import picosoft.javafro.base.Rhythm;
import picosoft.javafro.base.AudioConstants;
import picosoft.javafro.sequencer.PlaybackSettings;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Mixer state survives Stop/Play and belongs to the selected rhythm's tracks. */
final class MixerPanel extends JPanel {
    private final List<JSlider> volumes = new ArrayList<>();
    private final List<JToggleButton> mutes = new ArrayList<>();
    private List<String> signature = List.of();
    private PlaybackSettings settings;
    private final int initialVolume;
    private final JSlider master = new JSlider(0, 100, (int) (AudioConstants.OUTPUT_GAIN * 100));
    private final JPanel masterRow = new JPanel(new BorderLayout(10, 0));

    MixerPanel(int initialVolume) {
        super(new GridLayout(0, 1, 0, 4));
        this.initialVolume = initialVolume * 2;
        setBorder(BorderFactory.createTitledBorder("Mixer"));
        master.setName("masterVolume");
        master.getAccessibleContext().setAccessibleName("Master hlasitosť");
        JLabel label = new JLabel("Master");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setLabelFor(master);
        label.setPreferredSize(new Dimension(150, 24));
        JLabel value = new JLabel(master.getValue() + " %", SwingConstants.RIGHT);
        value.setPreferredSize(new Dimension(130, 24));
        masterRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        masterRow.add(label, BorderLayout.WEST);
        masterRow.add(master, BorderLayout.CENTER);
        masterRow.add(value, BorderLayout.EAST);
        master.addChangeListener(event -> {
            value.setText(master.getValue() + " %");
            if (settings != null) settings.setMasterVolume(master.getValue());
        });
        add(masterRow);
    }

    void reset() {
        settings = null;
        signature = List.of();
        volumes.clear(); mutes.clear(); removeAll();
        add(masterRow);
        revalidate(); repaint();
    }

    void setRhythm(Rhythm rhythm) {
        List<String> next = new ArrayList<>();
        next.add(rhythm.name());
        rhythm.tracks().forEach(track -> next.add(track.instrument().getClass().getName()));
        if (signature.equals(next)) return;
        signature = List.copyOf(next);
        settings = null;
        volumes.clear(); mutes.clear(); removeAll();
        add(masterRow);
        for (int i = 0; i < rhythm.tracks().size(); i++) {
            final int index = i;
            String instrument = rhythm.tracks().get(i).instrument().getClass().getSimpleName().replace("Instrument", "");
            String name = (i + 1) + " · " + instrument;
            JSlider volume = new JSlider(0, 200, initialVolume);
            volume.setName("trackVolume-" + i);
            volume.getAccessibleContext().setAccessibleName("Hlasitosť " + name);
            JToggleButton mute = new JToggleButton("Mute");
            mute.setName("trackMute-" + i);
            mute.getAccessibleContext().setAccessibleName("Mute " + name);
            JLabel value = new JLabel(trackVolumeText(initialVolume), SwingConstants.RIGHT);
            value.setPreferredSize(new Dimension(50, 24));
            JLabel label = new JLabel(name);
            label.setLabelFor(volume);
            label.setPreferredSize(new Dimension(150, 24));
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.add(value); right.add(mute);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.add(label, BorderLayout.WEST); row.add(volume, BorderLayout.CENTER); row.add(right, BorderLayout.EAST);
            volumes.add(volume); mutes.add(mute); add(row);
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

    private void update(int index) {
        if (settings != null) settings.setTrackMix(index, volumes.get(index).getValue(), mutes.get(index).isSelected());
    }

    private static String trackVolumeText(int volume) {
        return (volume % 2 == 0 ? Integer.toString(volume / 2) : Double.toString(volume / 2.0)) + " %";
    }
}
