package picosoft.pomodorotimer;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Countdown state and time calculations independent of Swing. */
public class PomodoroTimerModel {
    private final LongSupplier nanoTime;
    private int minutes = 25;
    private long deadlineNanos;
    private long remainingSeconds = TimeUnit.MINUTES.toSeconds(minutes);
    private boolean running;

    public PomodoroTimerModel() { this(System::nanoTime); }

    PomodoroTimerModel(LongSupplier nanoTime) { this.nanoTime = nanoTime; }

    public void setMinutes(int minutes) {
        if (minutes < 1 || minutes > 999) {
            throw new IllegalArgumentException("Minutes must be between 1 and 999.");
        }
        if (running) {
            throw new IllegalStateException("Cannot change duration while running.");
        }
        this.minutes = minutes;
        remainingSeconds = TimeUnit.MINUTES.toSeconds(minutes);
    }

    public void start() {
        remainingSeconds = TimeUnit.MINUTES.toSeconds(minutes);
        deadlineNanos = nanoTime.getAsLong() + TimeUnit.MINUTES.toNanos(minutes);
        running = true;
    }

    /** Returns true only on the update that completes a running countdown. */
    public boolean update() {
        if (!running) { return false; }
        long remainingNanos = deadlineNanos - nanoTime.getAsLong();
        if (remainingNanos <= 0) {
            remainingSeconds = 0;
            running = false;
            return true;
        }
        remainingSeconds = TimeUnit.NANOSECONDS.toSeconds(remainingNanos);
        if (remainingNanos % TimeUnit.SECONDS.toNanos(1) != 0) { remainingSeconds++; }
        return false;
    }

    public void cancel() {
        running = false;
        remainingSeconds = TimeUnit.MINUTES.toSeconds(minutes);
    }

    public boolean isRunning() { return running; }
    public long getRemainingSeconds() { return remainingSeconds; }
}
