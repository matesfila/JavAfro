package picosoft.javafro.ui;

import org.junit.Test;
import picosoft.javafro.base.Rhythm;
import picosoft.javafro.config.AppConfig;
import picosoft.javafro.sequencer.PlaybackSettings;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class PlaybackRestartTest {
    private static final String YAML = "First:\n  bpm: 90\n  stepsPerBeat: 4\n  tracks: {djembe: 'd.tt..s.'}\n"
            + "Second:\n  bpm: 140\n  timeSignature: '12/8'\n  tracks: {djembe: 'd..t..s.....'}\n"
            + "Third:\n  bpm: 80\n  stepsPerBeat: 4\n  tracks: {djembe: 'd.tt..s.'}\n";

    private static class Session implements RhythmPlayerPanel.PlaybackSession {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        volatile boolean stopped;
        Rhythm rhythm;
        PlaybackSettings controls;
        public void play(Rhythm rhythm, PlaybackSettings controls) throws Exception {
            this.rhythm = rhythm;
            this.controls = controls;
            started.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            finally { finished.countDown(); }
        }
        public void stop() { stopped = true; }
        public boolean isStopped() { return stopped; }
    }

    @Test
    public void probabilitySliderAppliesBeforePlayAndUpdatesTheRunningSession() throws Exception {
        var sessions = new CopyOnWriteArrayList<Session>();
        RhythmPlayerPanel panel = panel(sessions);
        try {
            SwingUtilities.invokeAndWait(() -> ((JSlider) named(panel, "humanizeProbabilitySlider")).setValue(65));
            click(panel, "playButton");
            Session session = sessions.getFirst();
            assertTrue(session.started.await(2, TimeUnit.SECONDS));
            assertEquals(65, session.controls.humanizeApplianceProbability());
            SwingUtilities.invokeAndWait(() -> ((JSlider) named(panel, "humanizeProbabilitySlider")).setValue(25));
            assertEquals(25, session.controls.humanizeApplianceProbability());
            assertEquals(1, sessions.size());
            assertFalse(session.stopped);
        } finally { close(panel, sessions); }
    }

    @Test
    public void repeatedPlayWaitsForOldSessionAndStartsOnlyLatestSelection() throws Exception {
        var sessions = new CopyOnWriteArrayList<Session>();
        RhythmPlayerPanel panel = panel(sessions);
        try {
            click(panel, "playButton");
            Session first = sessions.getFirst();
            assertTrue(first.started.await(2, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> assertTrue(((JButton) named(panel, "playButton")).isEnabled()));
            selectAndPlay(panel, "Second:");
            selectAndPlay(panel, "First:");
            selectAndPlay(panel, "Third:");
            assertTrue(first.stopped);
            assertEquals(1, sessions.size()); // The previous audio session still owns its device.
            first.release.countDown();
            awaitSessionCount(sessions, 2);
            Session next = sessions.get(1);
            assertTrue(next.started.await(2, TimeUnit.SECONDS));
            assertEquals(0, first.finished.getCount());
            assertEquals("Third", next.rhythm.name());
            assertEquals(80, next.controls.snapshot().bpm());
            assertEquals(4, next.controls.snapshot().dialect().offsets().size());
            assertEquals(2, sessions.size());
        } finally { close(panel, sessions); }
    }

    @Test
    public void stopCancelsAQueuedRestart() throws Exception {
        var sessions = new CopyOnWriteArrayList<Session>();
        RhythmPlayerPanel panel = panel(sessions);
        try {
            click(panel, "playButton");
            Session first = sessions.getFirst();
            assertTrue(first.started.await(2, TimeUnit.SECONDS));
            selectAndPlay(panel, "Second:");
            click(panel, "stopButton");
            first.release.countDown();
            assertTrue(first.finished.await(2, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            var status = new AtomicReference<String>();
            do {
                SwingUtilities.invokeAndWait(() -> status.set(((JLabel) named(panel, "playbackStatus")).getText()));
                if ("Zastavené".equals(status.get())) break;
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
            assertEquals("Zastavené", status.get());
            assertEquals(1, sessions.size());
        } finally { close(panel, sessions); }
    }

    private RhythmPlayerPanel panel(List<Session> sessions) throws Exception {
        var result = new AtomicReference<RhythmPlayerPanel>();
        SwingUtilities.invokeAndWait(() -> result.set(new RhythmPlayerPanel(YAML, AppConfig.defaults(), offset -> {
            var session = new Session(); sessions.add(session); return session;
        })));
        return result.get();
    }

    private void selectAndPlay(RhythmPlayerPanel panel, String key) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ((JTextArea) named(panel, "yamlEditor")).setCaretPosition(YAML.indexOf(key));
            ((JButton) named(panel, "playButton")).doClick(0);
        });
    }

    private void click(RhythmPlayerPanel panel, String button) throws Exception {
        SwingUtilities.invokeAndWait(() -> ((JButton) named(panel, button)).doClick(0));
    }

    private void close(RhythmPlayerPanel panel, List<Session> sessions) throws Exception {
        SwingUtilities.invokeAndWait(panel::stopPlayback);
        for (Session session : sessions) session.release.countDown();
    }

    private void awaitSessionCount(List<Session> sessions, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (sessions.size() < count && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(count, sessions.size());
    }

    private Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) {
                Component found = named(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
