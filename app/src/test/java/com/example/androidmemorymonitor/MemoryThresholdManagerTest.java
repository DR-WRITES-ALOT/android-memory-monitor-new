package com.example.androidmemorymonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for the 80% trigger / 75% reset hysteresis of {@link MemoryThresholdManager}. */
public class MemoryThresholdManagerTest {

    @Test
    public void triggersCleanupWhenUsageReachesEightyPercent() {
        MemoryThresholdManager manager = new MemoryThresholdManager();
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(79, true, true));
        assertEquals(MemoryThresholdManager.Action.TRIGGER_CLEANUP, manager.update(80, true, true));
        assertFalse(manager.isCycleArmed());
    }

    @Test
    public void triggersCleanupAboveEightyPercent() {
        MemoryThresholdManager manager = new MemoryThresholdManager();
        assertEquals(MemoryThresholdManager.Action.TRIGGER_CLEANUP, manager.update(93, true, true));
    }

    @Test
    public void doesNotRetriggerWhileUsageStaysAboveResetThreshold() {
        MemoryThresholdManager manager = new MemoryThresholdManager();
        assertEquals(MemoryThresholdManager.Action.TRIGGER_CLEANUP, manager.update(85, true, true));
        // The two-second refresh keeps sampling above 80%: no second cycle.
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(84, true, true));
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(80, true, true));
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(78, true, true));
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(76, true, true));
        // Exactly 75% is the reset threshold: that only re-arms, never cleans up.
        assertEquals(MemoryThresholdManager.Action.REARM, manager.update(75, true, true));
    }

    @Test
    public void percentBetweenResetAndTriggerKeepsCycleDisarmed() {
        MemoryThresholdManager manager = new MemoryThresholdManager();
        manager.update(85, true, true);
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(77, true, true));
        assertFalse(manager.isCycleArmed());
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(79, true, true));
        assertFalse(manager.isCycleArmed());
    }

    @Test
    public void rearmsAtResetThresholdAndTriggersAgainAtEighty() {
        MemoryThresholdManager manager = new MemoryThresholdManager();
        manager.update(82, true, true);
        assertEquals(MemoryThresholdManager.Action.REARM, manager.update(75, true, true));
        assertTrue(manager.isCycleArmed());
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(79, true, true));
        assertEquals(MemoryThresholdManager.Action.TRIGGER_CLEANUP, manager.update(81, true, true));
    }

    @Test
    public void reportsNoWorkersInsteadOfPretendingCleanupHappened() {
        MemoryThresholdManager manager = new MemoryThresholdManager();
        assertEquals(MemoryThresholdManager.Action.TRIGGER_CLEANUP_NO_WORKERS,
                manager.update(88, true, false));
        assertFalse(manager.isCycleArmed());
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(88, true, false));
    }

    @Test
    public void disabledAutoCleanupNeverTriggers() {
        MemoryThresholdManager manager = new MemoryThresholdManager();
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(95, false, true));
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(95, false, true));
        assertTrue(manager.isCycleArmed());
    }

    @Test
    public void warningFlagIndependentOfAutoCleanup() {
        assertTrue(MemoryThresholdManager.isWarning(80));
        assertTrue(MemoryThresholdManager.isWarning(100));
        assertFalse(MemoryThresholdManager.isWarning(79));
        assertFalse(MemoryThresholdManager.isWarning(0));
    }

    @Test
    public void resetRearmsTheCycle() {
        MemoryThresholdManager manager = new MemoryThresholdManager();
        manager.update(90, true, true);
        manager.reset();
        assertTrue(manager.isCycleArmed());
        assertEquals(MemoryThresholdManager.Action.TRIGGER_CLEANUP, manager.update(90, true, true));
    }

    @Test
    public void lowUsageIsAlwaysNoneWhileArmed() {
        MemoryThresholdManager manager = new MemoryThresholdManager();
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(10, true, true));
        assertEquals(MemoryThresholdManager.Action.NONE, manager.update(74, true, true));
        assertTrue(manager.isCycleArmed());
    }

    @Test
    public void cleanupReportContainsModeWorkersAndBothMeasurements() {
        String report = MemoryThresholdManager.formatCleanupReport(
                true, 3, 82, 12_884_901_888L, 16_106_127_360L, 74, 11_811_160_064L);
        assertTrue(report.contains("Automatic cleanup"));
        assertTrue(report.contains("3 workers"));
        assertTrue(report.contains("82%"));
        assertTrue(report.contains("74%"));
        String manual = MemoryThresholdManager.formatCleanupReport(
                false, 1, 61, 1L, 2L, 59, 1L);
        assertTrue(manual.contains("Manual cleanup"));
        assertTrue(manual.contains("1 worker"));
    }
}
