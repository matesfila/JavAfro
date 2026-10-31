package picosoft;

import picosoft.javafro.instruments.DundunInstrument;
import picosoft.javafro.instruments.KensereniInstrument;
import picosoft.javafro.instruments.SangbanInstrument;
import picosoft.javafro.sequencer.BeepSequencer;
import picosoft.javafro.sequencer.BeepTrack;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import java.awt.*;
import java.text.ParseException;
import java.util.concurrent.TimeUnit;

/**
 * Simple Pomodoro countdown with Start / Cancel controls.
 * Uses only standard Java Swing (no external dependencies).
 */
public class PomodoroTimer extends JFrame {

    private final JSpinner minutesSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 999, 1));
    private final JLabel countdownLabel = new JLabel("25:00", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Ready", SwingConstants.CENTER);
    private final JButton startButton = new JButton("Start");
    private final JButton cancelButton = new JButton("Cancel");

    private final Timer timer;
    private long deadlineNanos;

    public PomodoroTimer() {
        super("Pomodoro Timer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel settings = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        settings.add(new JLabel("Minutes:"));
        settings.add(minutesSpinner);

        countdownLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 36));

        JPanel center = new JPanel(new BorderLayout(5, 5));
        center.add(countdownLabel, BorderLayout.CENTER);
        center.add(statusLabel, BorderLayout.SOUTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        controls.add(startButton);
        controls.add(cancelButton);

        add(settings, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);

        timer = new Timer(200, e -> refreshCountdown());
        cancelButton.setEnabled(false);

        minutesSpinner.addChangeListener(e -> showInitialDuration());
        startButton.addActionListener(e -> startCountdown());
        cancelButton.addActionListener(e -> cancelCountdown());

        setSize(320, 210);
        setLocationRelativeTo(null);
    }

    private void startCountdown() {
        try {
            minutesSpinner.commitEdit();
        } catch (ParseException e) {
            JOptionPane.showMessageDialog(this, "Enter a valid number of minutes.");
            return;
        }

        int minutes = (Integer) minutesSpinner.getValue();
        deadlineNanos = System.nanoTime() + TimeUnit.MINUTES.toNanos(minutes);

        minutesSpinner.setEnabled(false);
        startButton.setEnabled(false);
        cancelButton.setEnabled(true);
        statusLabel.setText("Running");

        showDuration(TimeUnit.MINUTES.toSeconds(minutes));
        timer.start();
    }

    private void refreshCountdown() {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            timer.stop();
            showDuration(0);
            resetControls();
            statusLabel.setText("Finished");
            onTimerFinished();
            return;
        }

        // Round up so the timer initially shows the full selected duration.
        long remainingSeconds = TimeUnit.NANOSECONDS.toSeconds(remainingNanos);
        if (remainingNanos % TimeUnit.SECONDS.toNanos(1) != 0) {
            remainingSeconds++;
        }
        showDuration(remainingSeconds);
    }

    private void cancelCountdown() {
        timer.stop();
        resetControls();
        showInitialDuration();
        statusLabel.setText("Cancelled");
    }

    private void resetControls() {
        minutesSpinner.setEnabled(true);
        startButton.setEnabled(true);
        cancelButton.setEnabled(false);
    }

    private void showInitialDuration() {
        int minutes = (Integer) minutesSpinner.getValue();
        showDuration(TimeUnit.MINUTES.toSeconds(minutes));
    }

    private void showDuration(long totalSeconds) {
        countdownLabel.setText(String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60));
    }

    /** Called exactly once after a countdown finishes normally (not on Cancel). */
    protected void onTimerFinished() {
        try {
            new BeepSequencer().playPattern(java.util.List.of(
                    new BeepTrack("x...x...x.x.x...", new DundunInstrument()),
                    new BeepTrack("..x...xx", new SangbanInstrument()),
                    new BeepTrack("x.xx.xx.", new KensereniInstrument())
            ), 32, 116);
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PomodoroTimer().setVisible(true));
    }
}
