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
    private static final String YAML = "First:\n  bpm: 90\n  stepsPerBeat: 4\n  tracks: {djembe: 'b.tt..s.'}\n"
            + "Second:\n  bpm: 140\n  timeSignature: '12/8'\n  tracks: {djembe: 'b..t..s.....'}\n"
            + "Third:\n  bpm: 80\n  stepsPerBeat: 4\n  tracks: {djembe: 'b.tt..s.'}\n";

    private static class Session implements RhythmPlayerPanel.PlaybackSession {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        volatile boolean stopped;
        Rhythm rhythm;
        PlaybackSettings controls;
        Rhythm queuedRhythm;
        PlaybackSettings queuedControls;
        Runnable queuedStarted;
        public void play(Rhythm rhythm, PlaybackSettings controls) throws Exception {
            this.rhythm = rhythm;
            this.controls = controls;
            started.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            finally { finished.countDown(); }
        }
        public synchronized boolean queue(Rhythm rhythm, PlaybackSettings controls, Runnable started) {
            if (stopped) return false;
            queuedRhythm = rhythm; queuedControls = controls; queuedStarted = started;
            return true;
        }
        public synchronized void cancelQueued() { queuedRhythm = null; queuedControls = null; queuedStarted = null; }
        synchronized void boundary() {
            if (stopped || queuedRhythm == null) return;
            rhythm = queuedRhythm; controls = queuedControls;
            Runnable started = queuedStarted;
            cancelQueued();
            started.run();
        }
        public void stop() { stopped = true; cancelQueued(); }
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
    public void repeatedPlayQueuesOnlyLatestSelectionOnTheSameAudioSession() throws Exception {
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
            assertFalse(first.stopped);
            assertEquals(1, sessions.size());
            assertEquals("First", first.rhythm.name());
            assertEquals(90, first.controls.snapshot().bpm());
            assertEquals("Third", first.queuedRhythm.name());
            first.boundary();
            SwingUtilities.invokeAndWait(() -> {});
            assertEquals("Third", first.rhythm.name());
            assertEquals(80, first.controls.snapshot().bpm());
            assertEquals(4, first.controls.snapshot().dialect().offsets().size());
            assertEquals(1, sessions.size());
            assertEquals(1, first.finished.getCount());
        } finally { close(panel, sessions); }
    }

    @Test
    public void queuedSelectionKeepsRunningTempoAndSubdivisionUntilBoundary() throws Exception {
        var sessions = new CopyOnWriteArrayList<Session>();
        RhythmPlayerPanel panel = panel(sessions);
        try {
            click(panel, "playButton");
            Session session = sessions.getFirst();
            assertTrue(session.started.await(2, TimeUnit.SECONDS));
            selectAndPlay(panel, "Second:");
            assertEquals("First", session.rhythm.name());
            assertEquals(90, session.controls.snapshot().bpm());
            assertEquals(4, session.controls.snapshot().dialect().offsets().size());
            SwingUtilities.invokeAndWait(() -> ((JSlider) named(panel, "tempoSlider")).setValue(110));
            assertEquals(110, session.controls.snapshot().bpm());
            assertEquals(140, session.queuedControls.snapshot().bpm());
            session.boundary();
            SwingUtilities.invokeAndWait(() -> {});
            assertEquals("Second", session.rhythm.name());
            assertEquals(140, session.controls.snapshot().bpm());
            assertEquals(3, session.controls.snapshot().dialect().offsets().size());
            SwingUtilities.invokeAndWait(() -> ((JSlider) named(panel, "tempoSlider")).setValue(150));
            assertEquals(150, session.controls.snapshot().bpm());
        } finally { close(panel, sessions); }
    }

    @Test
    public void editsWhileQueuedAreAppliedOnlyByTheNextPlay() throws Exception {
        var sessions = new CopyOnWriteArrayList<Session>();
        RhythmPlayerPanel panel = panel(sessions);
        try {
            click(panel, "playButton");
            Session session = sessions.getFirst();
            assertTrue(session.started.await(2, TimeUnit.SECONDS));
            selectAndPlay(panel, "Second:");
            SwingUtilities.invokeAndWait(() -> ((JTextArea) named(panel, "yamlEditor")).setText(YAML.replace("bpm: 140", "bpm: 145")));
            session.boundary();
            SwingUtilities.invokeAndWait(() -> {});
            assertEquals(140, session.controls.snapshot().bpm());
            selectAndPlay(panel, "Second:");
            assertEquals(145, session.queuedControls.snapshot().bpm());
            assertEquals(140, session.controls.snapshot().bpm());
        } finally { close(panel, sessions); }
    }

    @Test
    public void playAfterStopWaitsForDeviceReleaseBeforeOpeningANewSession() throws Exception {
        var sessions = new CopyOnWriteArrayList<Session>();
        RhythmPlayerPanel panel = panel(sessions);
        try {
            click(panel, "playButton");
            Session first = sessions.getFirst();
            assertTrue(first.started.await(2, TimeUnit.SECONDS));
            click(panel, "stopButton");
            selectAndPlay(panel, "Second:");
            assertEquals(1, sessions.size());
            first.release.countDown();
            awaitSessionCount(sessions, 2);
            Session second = sessions.get(1);
            assertTrue(second.started.await(2, TimeUnit.SECONDS));
            assertEquals("Second", second.rhythm.name());
            assertEquals(140, second.controls.snapshot().bpm());
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
            var ready = new AtomicReference<Boolean>(false);
            do {
                SwingUtilities.invokeAndWait(() -> ready.set(((JButton) named(panel, "openButton")).isEnabled()));
                if (ready.get()) break;
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
            assertTrue(ready.get());
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(((JButton) named(panel, "playButton")).isEnabled());
                assertFalse(((JButton) named(panel, "stopButton")).isEnabled());
                assertEquals("", ((JLabel) named(panel, "playbackStatus")).getText());
            });
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
