package picosoft;

import picosoft.javafro.instruments.DundunInstrument;
import picosoft.javafro.instruments.KensereniInstrument;
import picosoft.javafro.instruments.SangbanInstrument;
import picosoft.javafro.sequencer.BeepSequencer;
import picosoft.javafro.sequencer.BeepTrack;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.ParseException;
import java.util.concurrent.TimeUnit;

/**
 * Simple Pomodoro countdown with Start / Cancel controls.
 * Minimizes to the system tray when supported (e.g. on Windows).
 * Uses only standard Java APIs; no external dependencies.
 */
public class PomodoroTimer extends JFrame {

    private final JSpinner minutesSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 999, 1));
    private final JLabel countdownLabel = new JLabel("25:00", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Ready", SwingConstants.CENTER);
    private final JButton startButton = new JButton("Start");
    private final JButton cancelButton = new JButton("Cancel");

    private final Timer timer;
    private long deadlineNanos;

    private final SystemTray systemTray = SystemTray.isSupported() ? SystemTray.getSystemTray() : null;
    private TrayIcon trayIcon;
    private boolean inTray;

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

        if (systemTray != null) {
            trayIcon = createTrayIcon();
            addWindowStateListener(e -> {
                if ((e.getNewState() & Frame.ICONIFIED) != 0) {
                    minimizeToTray();
                }
            });
        }

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

            // Do not run user code on the Swing Event Dispatch Thread (EDT).
            Thread worker = new Thread(() -> {
                try {
                    onTimerFinished();
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Action failed"));
                }
            }, "PomodoroTimer-onTimerFinished");
            worker.start();
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

    private void minimizeToTray() {
        if (systemTray == null || inTray) {
            return;
        }
        try {
            systemTray.add(trayIcon);
            inTray = true;
            setVisible(false);
        } catch (AWTException e) {
            // If the tray is unavailable, keep the window normally minimized.
            e.printStackTrace();
        }
    }

    private void restoreFromTray() {
        if (!inTray) {
            return;
        }
        inTray = false;
        systemTray.remove(trayIcon);
        setVisible(true);
        setExtendedState(Frame.NORMAL);
        toFront();
        requestFocus();
    }

    private TrayIcon createTrayIcon() {
        TrayIcon icon = new TrayIcon(createTrayImage(), "Pomodoro Timer");
        icon.setImageAutoSize(true);

        PopupMenu menu = new PopupMenu();
        MenuItem openItem = new MenuItem("Open");
        openItem.addActionListener(e -> SwingUtilities.invokeLater(this::restoreFromTray));
        menu.add(openItem);

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            systemTray.remove(icon);
            dispose();
            System.exit(0);
        }));
        menu.add(exitItem);
        icon.setPopupMenu(menu);

        // Left-click restores the window; double-click works as well.
        icon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    SwingUtilities.invokeLater(PomodoroTimer.this::restoreFromTray);
                }
            }
        });
        icon.addActionListener(e -> SwingUtilities.invokeLater(this::restoreFromTray));
        return icon;
    }

    private static Image createTrayImage() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xD95F45));
            g.fillOval(4, 4, 56, 56);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(12, 12, 40, 40);
            g.drawLine(32, 32, 32, 19);
            g.drawLine(32, 32, 42, 37);
        } finally {
            g.dispose();
        }
        return image;
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
            ), 32, 116);
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PomodoroTimer().setVisible(true));
    }
}
