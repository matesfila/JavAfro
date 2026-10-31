package picosoft.pomodorotimer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.ParseException;

/** Swing window and system tray presentation. */
public class PomodoroTimerView extends JFrame {

    private final JSpinner minutesSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 999, 1));
    private final JLabel countdownLabel = new JLabel("25:00", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Ready", SwingConstants.CENTER);
    private final JButton startButton = new JButton("Start");
    private final JButton cancelButton = new JButton("Cancel");

    private final SystemTray systemTray = SystemTray.isSupported() ? SystemTray.getSystemTray() : null;
    private TrayIcon trayIcon;
    private boolean inTray;

    public PomodoroTimerView() {
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

        cancelButton.setEnabled(false);

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

    public int getSelectedMinutes() throws ParseException {
        minutesSpinner.commitEdit();
        return (Integer) minutesSpinner.getValue();
    }

    public void onStart(Runnable action) { startButton.addActionListener(e -> action.run()); }
    public void onCancel(Runnable action) { cancelButton.addActionListener(e -> action.run()); }
    public void onDurationChanged(Runnable action) { minutesSpinner.addChangeListener(e -> action.run()); }

    public void setRunning(boolean running) {
        minutesSpinner.setEnabled(!running);
        startButton.setEnabled(!running);
        cancelButton.setEnabled(running);
    }

    public void showStatus(String status) { statusLabel.setText(status); }

    public void showInvalidDuration() {
        JOptionPane.showMessageDialog(this, "Enter a valid number of minutes.");
    }

    public void showDuration(long totalSeconds) {
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
                    SwingUtilities.invokeLater(PomodoroTimerView.this::restoreFromTray);
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

}
