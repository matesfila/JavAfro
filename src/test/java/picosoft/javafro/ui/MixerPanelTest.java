package picosoft.javafro.ui;

import org.junit.Test;
import picosoft.javafro.sequencer.PlaybackSettings;
import javax.swing.*;
import static org.junit.Assert.*;

public class MixerPanelTest {
    private static AbstractButton button(java.awt.Container container, String name) {
        for (var component : container.getComponents()) {
            if (component instanceof AbstractButton found && name.equals(found.getName())) return found;
            if (component instanceof java.awt.Container child) {
                AbstractButton found = button(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void soloUnmutesSelectedTrackAndMutesOthersWhilePreservingVolumes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var rhythm = RhythmPlayerPanel.parseRhythm("Solo:\n  tracks: {dundun: 'D.......', sangban: 'S.......', kensereni: 'K.......'}");
            var mixer = new MixerPanel(65);
            mixer.setRhythm(rhythm);
            var settings = new PlaybackSettings(120, 0);
            mixer.bind(settings);
            button(mixer, "trackMute-1").doClick();
            button(mixer, "trackSolo-1").doClick();
            for (int i = 0; i < 3; i++) {
                assertEquals(new PlaybackSettings.TrackMix(130, i != 1), settings.trackMix(i));
                assertEquals(i != 1, button(mixer, "trackMute-" + i).isSelected());
            }
            button(mixer, "trackSolo-0").doClick();
            assertTrue(button(mixer, "trackSolo-0").isSelected());
            button(mixer, "trackSolo-0").doClick();
            var restarted = new PlaybackSettings(120, 0);
            mixer.bind(restarted);
            for (int i = 0; i < 3; i++) {
                assertEquals(new PlaybackSettings.TrackMix(130, false), restarted.trackMix(i));
                assertFalse(button(mixer, "trackSolo-" + i).isSelected());
            }
            button(mixer, "trackMute-2").doClick();
            assertTrue(restarted.trackMix(2).muted());
        });
    }

    @Test public void mixerUsesOnlyCurrentYamlTracksWithTheirExactNames() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var mixer = new MixerPanel(65);
            var rhythm = RhythmPlayerPanel.parseRhythm("Test:\n  tracks: {dundun: 'D.......', dundun_withBell: 'D.......', sangbanBell: 'x.......', kensereni: 'K.......'}");
            mixer.setRhythm(rhythm);
            String[] names = {"dundun", "dundun_withBell", "sangbanBell", "kensereni"};
            assertEquals(names.length + 1, mixer.getComponentCount());
            for (int i = 0; i < names.length; i++) {
                JPanel row = (JPanel) mixer.getComponent(i + 1);
                assertEquals(names[i], ((JLabel) row.getComponent(0)).getText());
                assertEquals(names[i], rhythm.tracks().get(i).instrumentId());
            }
            var settings = new PlaybackSettings(120, 0);
            mixer.bind(settings);
            button(mixer, "trackSolo-0").doClick();
            assertFalse(settings.trackMix(0).muted());
            for (int i = 1; i < names.length; i++) assertTrue(settings.trackMix(i).muted());
            assertFalse(rhythm.tracks().get(0).instrument().supportsNote('x'));
            assertTrue(rhythm.tracks().get(1).instrument().supportsNote('x'));
            // Switching tracks under the same song name must refresh the mixer.
            mixer.setRhythm(RhythmPlayerPanel.parseRhythm("Test:\n  tracks: {kensereni_withBell: 'K.......'}"));
            mixer.setRhythm(RhythmPlayerPanel.parseRhythm("Test:\n  tracks: {kensereni: 'K.......'}"));
            assertEquals(2, mixer.getComponentCount());
            assertEquals("kensereni", ((JLabel) ((JPanel) mixer.getComponent(1)).getComponent(0)).getText());
        });
    }

    @Test
    public void configuredVolumeIsAppliedToPlaybackAndRestoredAfterReset() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var rhythm = RhythmPlayerPanel.parseRhythm("test:\n  tracks: {dundun_withBell: 'D.......', sangban_withBell: 'S.......'}");
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
