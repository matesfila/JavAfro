package picosoft.pomodorotimer;

import picosoft.javafro.instruments.synth.DundunInstrument;
import picosoft.javafro.instruments.synth.KensereniInstrument;
import picosoft.javafro.instruments.synth.SangbanInstrument;
import picosoft.javafro.sequencer.BeepSequencer;
import picosoft.javafro.sequencer.BeepTrack;
import picosoft.windows.WindowsDesktop;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import java.awt.*;
import java.text.ParseException;

/** Controller connecting the countdown model and Swing view. */
public class PomodoroTimer {
    private final PomodoroTimerModel model = new PomodoroTimerModel();
    private final PomodoroTimerView view = new PomodoroTimerView();
    private final Timer timer;
    private boolean awaitingAcknowledgement;

    public PomodoroTimer() {
        timer = new Timer(200, e -> refreshCountdown());
        view.onStart(this::startCountdown);
        view.onCancel(this::cancelCountdown);
        view.onDurationChanged(this::showInitialDuration);
        view.showDuration(model.getRemainingSeconds());
    }

    public void setVisible(boolean visible) { view.setVisible(visible); }

    private void startCountdown() {
        if (awaitingAcknowledgement) {
            return;
        }
        try {
            model.setMinutes(view.getSelectedMinutes());
        } catch (ParseException e) {
            view.showInvalidDuration();
            return;
        }
        model.start();
        view.setRunning(true);
        view.showStatus("Running");
        view.showDuration(model.getRemainingSeconds());
        timer.start();
    }

    private void refreshCountdown() {
        boolean finished = model.update();
        view.showDuration(model.getRemainingSeconds());
        if (finished) {
            timer.stop();
            awaitingAcknowledgement = true;
            view.setAwaitingAcknowledgement();
            view.showStatus("Finished");
            // Read Swing components on the EDT before starting the background action.
            boolean playSound = view.isPlaySoundSelected();
            boolean showDesktop = view.isShowDesktopSelected();
            Thread worker = new Thread(() -> {
                try {
                    onTimerFinished(playSound, showDesktop);
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> view.showStatus("Action failed"));
                }
            }, "PomodoroTimer-onTimerFinished");
            worker.start();
        }
    }

    private void cancelCountdown() {
        timer.stop();
        model.cancel();
        view.setRunning(false);
        view.showDuration(model.getRemainingSeconds());
        view.showStatus("Cancelled");
    }

    private void showInitialDuration() {
        if (model.isRunning() || awaitingAcknowledgement) { return; }
        try {
            model.setMinutes(view.getSelectedMinutes());
            view.showDuration(model.getRemainingSeconds());
        } catch (ParseException e) {
            view.showInvalidDuration();
        }
    }

    /**
     * Called once after each normally completed countdown (not on Cancel).
     * Runs on a background thread, NOT on the Swing EDT.
     * Use SwingUtilities.invokeLater(...) to update Swing components from here.
     * The options are captured from the view when the countdown finishes.
     */
    protected void onTimerFinished(boolean playSound, boolean showDesktop) {
        BeepSequencer alarm = new BeepSequencer();
        try {
            if (showDesktop) {
                TimerFineshedEvents.showDeskop();
            }
        } catch (RuntimeException e) {
            // A desktop failure must not prevent acknowledgement or sound playback.
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            try {
                view.showCompletionDialog();
            } finally {
                alarm.stop();
                model.cancel();
                awaitingAcknowledgement = false;
                view.showDuration(model.getRemainingSeconds());
                view.showStatus("Ready");
                view.setRunning(false);
            }
        });
        if (playSound) {
            try {
                TimerFineshedEvents.playsound(alarm, Integer.MAX_VALUE);
            } catch (RuntimeException e) {
                if (!alarm.isStopped()) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        if (awaitingAcknowledgement) {
                            view.showStatus("Sound unavailable");
                        }
                    });
                }
            }
        }
    }

    public static class TimerFineshedEvents {

        public static void playsound() {
            playsound(new BeepSequencer(), 8);
        }

        private static void playsound(BeepSequencer sequencer, int repetitions) {
            try {
                sequencer.playPattern(java.util.List.of(
                        new BeepTrack("x...x...x.x.x...", new DundunInstrument()),
                        new BeepTrack("..x...xx", new SangbanInstrument()),
                        new BeepTrack("x.xx.xx.", new KensereniInstrument())
                ), repetitions, 116);
            } catch (LineUnavailableException e) {
                throw new RuntimeException(e);
            }
        }


        public static void showDeskop() {
            try {
                WindowsDesktop.showDesktop();
            } catch (AWTException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PomodoroTimerView.configureLookAndFeel();
            new PomodoroTimer().setVisible(true);
        });
    }
}
