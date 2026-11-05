package picosoft.javafro.ui;

import org.junit.Test;
import picosoft.javafro.sequencer.PlaybackSettings;
import javax.swing.*;
import static org.junit.Assert.*;

public class MixerPanelTest {
    @Test
    public void configuredVolumeIsAppliedToPlaybackAndRestoredAfterReset() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var rhythm = RhythmPlayerPanel.parseRhythm("test:\n  tracks: {dundun: 'D.......', sangban: 'S.......'}");
            var mixer = new MixerPanel(65);
            mixer.setRhythm(rhythm);
            var settings = new PlaybackSettings(120, 0);
            mixer.bind(settings);
            assertEquals(new PlaybackSettings.TrackMix(130, false), settings.trackMix(0));
            assertEquals(new PlaybackSettings.TrackMix(130, false), settings.trackMix(1));
            JPanel masterRow = (JPanel) mixer.getComponent(0);
            assertEquals(25, settings.masterVolume());
            for (var component : masterRow.getComponents()) {
                if (component instanceof JSlider volume) volume.setValue(50);
            }
            assertEquals(50, settings.masterVolume());
            JPanel row = (JPanel) mixer.getComponent(1);
            for (var component : row.getComponents()) {
                if (component instanceof JSlider volume) volume.setValue(30);
            }
            assertEquals(30, settings.trackMix(0).volume());
            mixer.reset();
            mixer.setRhythm(rhythm);
            mixer.bind(settings);
            assertEquals(new PlaybackSettings.TrackMix(130, false), settings.trackMix(0));
            assertEquals(50, settings.masterVolume());
        });
    }
}
