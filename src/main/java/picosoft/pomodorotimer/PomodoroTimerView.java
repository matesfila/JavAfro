package picosoft.pomodorotimer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.ParseException;

/** Swing window and system tray presentation. */
public class PomodoroTimerView extends JFrame {

    private static final Color BACKGROUND = new Color(0xF5F6F8);
    private static final Color TEXT = new Color(0x202938);
    private static final Color MUTED = new Color(0x677386);
    private static final Color ACCENT = new Color(0xC64E36);
    private static final Font UI_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    private final JSpinner minutesSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 999, 1));
    private final JLabel countdownLabel = new JLabel("25:00", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Ready", SwingConstants.CENTER);
    private final JButton startButton = new JButton("Start");
    private final JButton cancelButton = new JButton("Cancel");
    private final JCheckBox playSoundCheckBox = new JCheckBox("Play sound", true);
    private final JCheckBox showDesktopCheckBox = new JCheckBox("Show desktop", true);

    private final SystemTray systemTray = SystemTray.isSupported() ? SystemTray.getSystemTray() : null;
    private TrayIcon trayIcon;
    private boolean inTray;

    public PomodoroTimerView() {
        super("Pomodoro Timer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 22));
        content.setBackground(BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(26, 28, 26, 28));
        setContentPane(content);

        JLabel title = new JLabel("Focus time", SwingConstants.CENTER);
        title.setFont(UI_FONT.deriveFont(Font.BOLD, 24f));
        title.setForeground(TEXT);
        JLabel subtitle = new JLabel("One task. One focused session.", SwingConstants.CENTER);
        subtitle.setFont(UI_FONT);
        subtitle.setForeground(MUTED);
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);

        JPanel countdown = new JPanel(new BorderLayout(0, 8));
        countdown.setBackground(Color.WHITE);
        countdown.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE2E6EC)),
                BorderFactory.createEmptyBorder(24, 20, 24, 20)));
        countdownLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 64));
        countdownLabel.setForeground(TEXT);
        statusLabel.setFont(UI_FONT.deriveFont(Font.BOLD, 13f));
        statusLabel.setForeground(MUTED);
        countdown.add(countdownLabel, BorderLayout.CENTER);
        countdown.add(statusLabel, BorderLayout.SOUTH);
        content.add(countdown, BorderLayout.CENTER);

        JLabel durationLabel = new JLabel("Duration (min)");
        durationLabel.setFont(UI_FONT);
        durationLabel.setForeground(MUTED);
        durationLabel.setLabelFor(minutesSpinner);
        minutesSpinner.setFont(UI_FONT);
        minutesSpinner.setPreferredSize(new Dimension(100, 36));
        JSpinner.NumberEditor editor = (JSpinner.NumberEditor) minutesSpinner.getEditor();
        editor.getTextField().setFont(UI_FONT);
        editor.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        settings.setOpaque(false);
        settings.add(durationLabel);
        settings.add(minutesSpinner);

        styleButton(startButton, ACCENT, Color.WHITE);
        styleButton(cancelButton, new Color(0xE7EBF0), TEXT);
        JPanel controls = new JPanel(new GridLayout(1, 2, 12, 0));
        controls.setOpaque(false);
        controls.add(startButton);
        controls.add(cancelButton);
        JPanel footer = new JPanel(new BorderLayout(0, 18));
        footer.setOpaque(false);
        footer.add(settings, BorderLayout.NORTH);
        JPanel completionOptions = new JPanel(new GridLayout(0, 1, 0, 4));
        completionOptions.setOpaque(false);
        JLabel completionLabel = new JLabel("When the timer finishes");
        completionLabel.setFont(UI_FONT.deriveFont(Font.BOLD, 13f));
        completionLabel.setForeground(MUTED);
        completionOptions.add(completionLabel);
        for (JCheckBox checkBox : new JCheckBox[]{playSoundCheckBox, showDesktopCheckBox}) {
            checkBox.setFont(UI_FONT);
            checkBox.setForeground(TEXT);
            checkBox.setOpaque(false);
            completionOptions.add(checkBox);
        }
        showDesktopCheckBox.setToolTipText("Sends Win+D; can restore windows if the desktop is already shown.");
        footer.add(completionOptions, BorderLayout.CENTER);
        footer.add(controls, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(startButton);
        setIconImage(createTrayImage());

        cancelButton.setEnabled(false);

        if (systemTray != null) {
            trayIcon = createTrayIcon();
            addWindowStateListener(e -> {
                if ((e.getNewState() & Frame.ICONIFIED) != 0) {
                    minimizeToTray();
                }
            });
        }

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    /** Configure Swing before constructing the view and its components. */
    public static void configureLookAndFeel() {
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (ClassNotFoundException | InstantiationException
                         | IllegalAccessException | UnsupportedLookAndFeelException e) {
                    // Keep the default look and feel if Nimbus cannot be loaded.
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    private static void styleButton(JButton button, Color background, Color foreground) {
        button.setFont(UI_FONT.deriveFont(Font.BOLD));
        button.setBackground(background);
        button.setForeground(foreground);
        button.setMargin(new Insets(12, 20, 12, 20));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public int getSelectedMinutes() throws ParseException {
        minutesSpinner.commitEdit();
        return (Integer) minutesSpinner.getValue();
    }

    public void onStart(Runnable action) { startButton.addActionListener(e -> action.run()); }
    public void onCancel(Runnable action) { cancelButton.addActionListener(e -> action.run()); }
    public void onDurationChanged(Runnable action) { minutesSpinner.addChangeListener(e -> action.run()); }

    public boolean isPlaySoundSelected() { return playSoundCheckBox.isSelected(); }
    public boolean isShowDesktopSelected() { return showDesktopCheckBox.isSelected(); }

    public void setRunning(boolean running) {
        minutesSpinner.setEnabled(!running);
        startButton.setEnabled(!running);
        cancelButton.setEnabled(running);
    }

    public void setAwaitingAcknowledgement() {
        minutesSpinner.setEnabled(false);
        startButton.setEnabled(false);
        cancelButton.setEnabled(false);
    }

    /** Called on the EDT; returns when OK is pressed or the dialog is closed. */
    public void showCompletionDialog() {
        restoreWindow();
        JOptionPane.showMessageDialog(this,
                "Your focus session is complete.\nPress OK to stop the alarm and prepare the next session.",
                "Session complete", JOptionPane.PLAIN_MESSAGE);
    }

    public void showStatus(String status) {
        statusLabel.setText(status);
        statusLabel.setForeground(switch (status) {
            case "Running" -> ACCENT;
            case "Finished" -> new Color(0x237A54);
            case "Action failed" -> new Color(0xB42318);
            default -> MUTED;
        });
    }

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

    public void restoreWindow() {
        if (inTray) {
            inTray = false;
            systemTray.remove(trayIcon);
        }
        setExtendedState(getExtendedState() & ~Frame.ICONIFIED);
        setVisible(true);
        toFront();
        requestFocus();
    }

    private TrayIcon createTrayIcon() {
        TrayIcon icon = new TrayIcon(createTrayImage(), "Pomodoro Timer");
        icon.setImageAutoSize(true);

        PopupMenu menu = new PopupMenu();
        MenuItem openItem = new MenuItem("Open");
        openItem.addActionListener(e -> SwingUtilities.invokeLater(this::restoreWindow));
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
                    SwingUtilities.invokeLater(PomodoroTimerView.this::restoreWindow);
                }
            }
        });
        icon.addActionListener(e -> SwingUtilities.invokeLater(this::restoreWindow));
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
