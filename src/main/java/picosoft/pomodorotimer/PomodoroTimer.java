package picosoft.pomodorotimer;

import picosoft.javafro.instruments.DundunInstrument;
import picosoft.javafro.instruments.KensereniInstrument;
import picosoft.javafro.instruments.SangbanInstrument;
import picosoft.javafro.sequencer.BeepSequencer;
import picosoft.javafro.sequencer.BeepTrack;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import java.text.ParseException;

/** Controller connecting the countdown model and Swing view. */
public class PomodoroTimer {
    private final PomodoroTimerModel model = new PomodoroTimerModel();
    private final PomodoroTimerView view = new PomodoroTimerView();
    private final Timer timer;

    public PomodoroTimer() {
        timer = new Timer(200, e -> refreshCountdown());
        view.onStart(this::startCountdown);
        view.onCancel(this::cancelCountdown);
        view.onDurationChanged(this::showInitialDuration);
        view.showDuration(model.getRemainingSeconds());
    }

    public void setVisible(boolean visible) { view.setVisible(visible); }

    private void startCountdown() {
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
            view.setRunning(false);
            view.showStatus("Finished");
            Thread worker = new Thread(() -> {
                try {
                    onTimerFinished();
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
        if (model.isRunning()) { return; }
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
     */
    protected void onTimerFinished() {
        try {
            new BeepSequencer().playPattern(java.util.List.of(
                    new BeepTrack("x...x...x.x.x...", new DundunInstrument()),
                    new BeepTrack("..x...xx", new SangbanInstrument()),
                    new BeepTrack("x.xx.xx.", new KensereniInstrument())
            ), 8, 116);
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PomodoroTimer().setVisible(true));
    }
}
