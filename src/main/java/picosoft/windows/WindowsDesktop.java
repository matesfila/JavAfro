package picosoft.windows;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;

/** Windows desktop shortcuts implemented using standard Java APIs. */
public final class WindowsDesktop {
    private WindowsDesktop() {
    }

    /**
     * Sends Win+D to toggle between the desktop and the open windows.
     * Calling this again can restore the windows rather than minimize them.
     *
     * @throws UnsupportedOperationException if the operating system is not Windows
     * @throws AWTException if native keyboard input is unavailable
     */
    public static void showDesktop() throws AWTException {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            throw new UnsupportedOperationException("This shortcut requires Windows.");
        }

        Robot robot = new Robot();
        robot.keyPress(KeyEvent.VK_WINDOWS);
        try {
            robot.keyPress(KeyEvent.VK_D);
            try {
                robot.delay(50);
            } finally {
                robot.keyRelease(KeyEvent.VK_D);
            }
        } finally {
            robot.keyRelease(KeyEvent.VK_WINDOWS);
        }
    }
}
