package com.example.androidmemorymonitor;

/**
 * Pure-Java memory-pressure state machine.
 *
 * <p>Keeps the hysteresis rules for automatic demo-worker cleanup in one testable place:
 * a cleanup cycle is triggered when system RAM usage reaches {@link #TRIGGER_PERCENT}
 * (80%) and the cycle is armed. After a cycle the system must fall back to
 * {@link #RESET_PERCENT} (75%) or below before another automatic cycle is allowed, so the
 * cleanup does not fire on every two-second refresh while memory stays high.</p>
 *
 * <p>This class has no Android dependencies so the threshold behaviour can be covered by
 * plain JVM unit tests.</p>
 */
public final class MemoryThresholdManager {

    /** RAM usage percentage at or above which automatic cleanup may trigger. */
    public static final int TRIGGER_PERCENT = 80;

    /** RAM usage percentage at or below which the next cleanup cycle is re-armed. */
    public static final int RESET_PERCENT = 75;

    /** What the caller should do for a given refresh sample. */
    public enum Action {
        /** Nothing special: update the warning banner only. */
        NONE,
        /** Usage dropped to the reset threshold; a new automatic cycle is armed. */
        REARM,
        /** Run automatic cleanup now: demo workers exist and should be stopped. */
        TRIGGER_CLEANUP,
        /**
         * Usage crossed the trigger threshold but no demo workers exist. Show an honest
         * message ("nothing to clean up") instead of pretending cleanup happened.
         */
        TRIGGER_CLEANUP_NO_WORKERS
    }

    private boolean cycleArmed = true;

    /**
     * Feeds one RAM-usage sample into the state machine.
     *
     * @param usagePercent       current system RAM usage in percent (0-100)
     * @param autoCleanupEnabled whether the user enabled automatic cleanup
     * @param hasWorkers         whether at least one demo worker process is running
     * @return the action the caller should take for this sample
     */
    public Action update(int usagePercent, boolean autoCleanupEnabled, boolean hasWorkers) {
        if (usagePercent <= RESET_PERCENT) {
            boolean wasArmed = cycleArmed;
            cycleArmed = true;
            return wasArmed ? Action.NONE : Action.REARM;
        }
        if (autoCleanupEnabled && usagePercent >= TRIGGER_PERCENT && cycleArmed) {
            cycleArmed = false;
            return hasWorkers ? Action.TRIGGER_CLEANUP : Action.TRIGGER_CLEANUP_NO_WORKERS;
        }
        return Action.NONE;
    }

    /** True while a new automatic cleanup cycle may trigger. */
    public boolean isCycleArmed() {
        return cycleArmed;
    }

    /** Manual cleanup and lifecycle resets re-arm the automatic cycle. */
    public void reset() {
        cycleArmed = true;
    }

    /** The dashboard warning banner shows at or above the trigger threshold. */
    public static boolean isWarning(int usagePercent) {
        return usagePercent >= TRIGGER_PERCENT;
    }

    /** Formats the before/after line shown in the cleanup report. */
    public static String formatCleanupReport(boolean automatic, int workersRemoved,
            int beforePercent, long beforeUsedBytes, long beforeTotalBytes,
            int afterPercent, long afterUsedBytes) {
        String mode = automatic ? "Automatic cleanup" : "Manual cleanup";
        String workers = workersRemoved == 1 ? "1 worker" : workersRemoved + " workers";
        return String.format(java.util.Locale.US,
                "%s removed %s. RAM %.2f GiB used (%d%%) before, %.2f GiB used (%d%%) after.",
                mode, workers,
                beforeUsedBytes / (1024.0 * 1024.0 * 1024.0), beforePercent,
                afterUsedBytes / (1024.0 * 1024.0 * 1024.0), afterPercent);
    }
}
