package com.example.androidmemorymonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Unit tests for {@link SchedulerSimulator}: FCFS, Round Robin, waiting and turnaround times. */
public class SchedulerSimulatorTest {

    private static List<SchedulingJob> jobs(SchedulingJob... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static SchedulingJob job(String id, int arrival, int burst) {
        return new SchedulingJob(id, "process-" + id, arrival, burst);
    }

    // ------------------------------------------------------------------ FCFS

    @Test
    public void fcfsClassicExampleRunsJobsInArrivalOrder() {
        List<SchedulingJob> input = jobs(job("P1", 0, 24), job("P2", 0, 3), job("P3", 0, 3));
        ScheduleResult result = SchedulerSimulator.simulateFcfs(input);

        assertEquals(3, result.getSlices().size());
        assertEquals("P1", result.getSlices().get(0).jobId);
        assertEquals(0, result.getSlices().get(0).startTime);
        assertEquals(24, result.getSlices().get(0).endTime);
        assertEquals("P2", result.getSlices().get(1).jobId);
        assertEquals("P3", result.getSlices().get(2).jobId);
        assertEquals(30, result.getTotalTime());

        // Waiting: P1=0, P2=24, P3=27 -> avg 17. Turnaround: 24,27,30 -> avg 27.
        assertEquals(0, result.getTiming("P1").waitingTime);
        assertEquals(24, result.getTiming("P2").waitingTime);
        assertEquals(27, result.getTiming("P3").waitingTime);
        assertEquals(17.0, result.getAverageWaitingTime(), 1e-9);
        assertEquals(27.0, result.getAverageTurnaroundTime(), 1e-9);
    }

    @Test
    public void fcfsWithArrivalGapsInsertsIdleSlice() {
        List<SchedulingJob> input = jobs(job("P1", 0, 5), job("P2", 3, 3), job("P3", 10, 8));
        ScheduleResult result = SchedulerSimulator.simulateFcfs(input);

        assertEquals(4, result.getSlices().size());
        ScheduleResult.Slice idle = result.getSlices().get(2);
        assertTrue(idle.isIdle());
        assertEquals(8, idle.startTime);
        assertEquals(10, idle.endTime);

        assertEquals(5, result.getTiming("P1").completionTime);
        assertEquals(8, result.getTiming("P2").completionTime);
        assertEquals(18, result.getTiming("P3").completionTime);
        assertEquals(0, result.getTiming("P1").waitingTime);
        assertEquals(2, result.getTiming("P2").waitingTime);
        assertEquals(0, result.getTiming("P3").waitingTime);
        assertEquals(2.0 / 3.0, result.getAverageWaitingTime(), 1e-9);
        assertEquals(6.0, result.getAverageTurnaroundTime(), 1e-9);
    }

    @Test
    public void fcfsSingleJobHasZeroWaiting() {
        ScheduleResult result = SchedulerSimulator.simulateFcfs(jobs(job("P1", 4, 7)));
        assertEquals(2, result.getSlices().size());
        assertTrue(result.getSlices().get(0).isIdle());
        assertEquals(4, result.getSlices().get(0).endTime);
        assertEquals(11, result.getTotalTime());
        assertEquals(0, result.getTiming("P1").waitingTime);
        assertEquals(7, result.getTiming("P1").turnaroundTime);
    }

    @Test
    public void fcfsEmptyInputYieldsEmptyResult() {
        ScheduleResult result = SchedulerSimulator.simulateFcfs(Collections.emptyList());
        assertTrue(result.getSlices().isEmpty());
        assertEquals(0.0, result.getAverageWaitingTime(), 1e-9);
        assertEquals(0.0, result.getAverageTurnaroundTime(), 1e-9);
        assertEquals(0, result.getTotalTime());
    }

    @Test
    public void fcfsDoesNotMutateInputJobs() {
        List<SchedulingJob> input = jobs(job("P1", 0, 5), job("P2", 1, 3));
        SchedulerSimulator.simulateFcfs(input);
        assertEquals(5, input.get(0).getBurstTime());
        assertEquals(5, input.get(0).getRemainingBurst());
        assertEquals(SchedulingJob.State.READY, input.get(0).getState());
    }

    // ------------------------------------------------------------------ Round Robin

    @Test
    public void roundRobinClassicExampleWithQuantumFour() {
        List<SchedulingJob> input = jobs(job("P1", 0, 24), job("P2", 0, 3), job("P3", 0, 3));
        ScheduleResult result = SchedulerSimulator.simulateRoundRobin(input, 4);

        // P1(0-4), P2(4-7), P3(7-10), then P1 in quantum slices until 30.
        assertEquals(8, result.getSlices().size());
        assertEquals("P1", result.getSlices().get(0).jobId);
        assertEquals("P2", result.getSlices().get(1).jobId);
        assertEquals("P3", result.getSlices().get(2).jobId);
        assertEquals(30, result.getTotalTime());

        assertEquals(7, result.getTiming("P2").completionTime);
        assertEquals(10, result.getTiming("P3").completionTime);
        assertEquals(30, result.getTiming("P1").completionTime);
        // Waiting: P1=6, P2=4, P3=7 -> 17/3. Turnaround: 30,7,10 -> 47/3.
        assertEquals(6, result.getTiming("P1").waitingTime);
        assertEquals(4, result.getTiming("P2").waitingTime);
        assertEquals(7, result.getTiming("P3").waitingTime);
        assertEquals(17.0 / 3.0, result.getAverageWaitingTime(), 1e-9);
        assertEquals(47.0 / 3.0, result.getAverageTurnaroundTime(), 1e-9);
    }

    @Test
    public void roundRobinAdmitsArrivalsDuringQuantumBeforeRequeue() {
        // P2 arrives at t=1 while P1 is running its slice [0,2]; P2 must run before the
        // preempted P1 is re-queued at the back.
        List<SchedulingJob> input = jobs(job("P1", 0, 3), job("P2", 1, 2));
        ScheduleResult result = SchedulerSimulator.simulateRoundRobin(input, 2);

        assertEquals(3, result.getSlices().size());
        assertEquals("P1", result.getSlices().get(0).jobId);
        assertEquals(0, result.getSlices().get(0).startTime);
        assertEquals("P2", result.getSlices().get(1).jobId);
        assertEquals(2, result.getSlices().get(1).startTime);
        assertEquals("P1", result.getSlices().get(2).jobId);
        assertEquals(4, result.getSlices().get(2).startTime);

        assertEquals(5, result.getTiming("P1").completionTime);
        assertEquals(4, result.getTiming("P2").completionTime);
        assertEquals(2, result.getTiming("P1").waitingTime);
        assertEquals(1, result.getTiming("P2").waitingTime);
    }

    @Test
    public void roundRobinWithStaggeredArrivalsAndPreemption() {
        // Arrivals during a quantum join the ready queue at the next scheduling point:
        // P1(0-2), P2(2-4), P3(4-6), P1(6-8), P2(8-9), P3(9-11), P1(11-12), P3(12-16).
        List<SchedulingJob> input = jobs(job("P1", 0, 5), job("P2", 1, 3), job("P3", 2, 8));
        ScheduleResult result = SchedulerSimulator.simulateRoundRobin(input, 2);

        assertEquals(16, result.getTotalTime());
        assertEquals(12, result.getTiming("P1").completionTime);
        assertEquals(9, result.getTiming("P2").completionTime);
        assertEquals(16, result.getTiming("P3").completionTime);
        assertEquals(7, result.getTiming("P1").waitingTime);
        assertEquals(5, result.getTiming("P2").waitingTime);
        assertEquals(6, result.getTiming("P3").waitingTime);
        assertEquals(6.0, result.getAverageWaitingTime(), 1e-9);
        assertEquals(34.0 / 3.0, result.getAverageTurnaroundTime(), 1e-9);
    }

    @Test
    public void roundRobinInsertsIdleBeforeFirstArrival() {
        List<SchedulingJob> input = jobs(job("P1", 2, 3));
        ScheduleResult result = SchedulerSimulator.simulateRoundRobin(input, 2);

        assertEquals(3, result.getSlices().size());
        assertTrue(result.getSlices().get(0).isIdle());
        assertEquals(0, result.getSlices().get(0).startTime);
        assertEquals(2, result.getSlices().get(0).endTime);
        assertEquals(5, result.getTotalTime());
        assertEquals(0, result.getTiming("P1").waitingTime);
        assertEquals(3, result.getTiming("P1").turnaroundTime);
    }

    @Test
    public void roundRobinQuantumOneAlternatesFairly() {
        List<SchedulingJob> input = jobs(job("P1", 0, 2), job("P2", 0, 2));
        ScheduleResult result = SchedulerSimulator.simulateRoundRobin(input, 1);

        assertEquals(4, result.getSlices().size());
        assertEquals("P1", result.getSlices().get(0).jobId);
        assertEquals("P2", result.getSlices().get(1).jobId);
        assertEquals("P1", result.getSlices().get(2).jobId);
        assertEquals("P2", result.getSlices().get(3).jobId);
        assertEquals(3, result.getTiming("P1").completionTime);
        assertEquals(4, result.getTiming("P2").completionTime);
    }

    @Test(expected = IllegalArgumentException.class)
    public void roundRobinRejectsZeroQuantum() {
        SchedulerSimulator.simulateRoundRobin(jobs(job("P1", 0, 1)), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void roundRobinRejectsNegativeQuantum() {
        SchedulerSimulator.simulateRoundRobin(jobs(job("P1", 0, 1)), -3);
    }

    @Test
    public void roundRobinEmptyInputYieldsEmptyResult() {
        ScheduleResult result = SchedulerSimulator.simulateRoundRobin(Collections.emptyList(), 3);
        assertTrue(result.getSlices().isEmpty());
        assertEquals(0.0, result.getAverageWaitingTime(), 1e-9);
    }

    @Test
    public void roundRobinDoesNotMutateInputJobs() {
        List<SchedulingJob> input = jobs(job("P1", 0, 5), job("P2", 1, 3));
        SchedulerSimulator.simulateRoundRobin(input, 2);
        assertEquals(5, input.get(0).getRemainingBurst());
        assertEquals(3, input.get(1).getRemainingBurst());
    }

    // ------------------------------------------------------ shared invariants

    @Test
    public void waitingTimeAlwaysEqualsTurnaroundMinusBurst() {
        List<SchedulingJob> input = jobs(
                job("P1", 0, 5), job("P2", 1, 3), job("P3", 2, 8), job("P4", 6, 2));
        for (ScheduleResult result : Arrays.asList(
                SchedulerSimulator.simulateFcfs(input),
                SchedulerSimulator.simulateRoundRobin(input, 2),
                SchedulerSimulator.simulateRoundRobin(input, 4))) {
            for (ScheduleResult.JobTiming timing : result.getTimings()) {
                assertEquals(timing.jobId,
                        timing.turnaroundTime - timing.burstTime, timing.waitingTime);
                assertEquals(timing.jobId,
                        timing.completionTime - timing.arrivalTime, timing.turnaroundTime);
            }
        }
    }

    @Test
    public void slicesAreContiguousAndCoverTheWholeTimeline() {
        List<SchedulingJob> input = jobs(job("P1", 0, 5), job("P2", 3, 3), job("P3", 10, 8));
        for (ScheduleResult result : Arrays.asList(
                SchedulerSimulator.simulateFcfs(input),
                SchedulerSimulator.simulateRoundRobin(input, 2))) {
            int expectedStart = 0;
            for (ScheduleResult.Slice slice : result.getSlices()) {
                assertEquals(expectedStart, slice.startTime);
                assertTrue(slice.endTime > slice.startTime);
                expectedStart = slice.endTime;
            }
            assertEquals(expectedStart, result.getTotalTime());
        }
    }

    @Test
    public void jobModelValidatesBurstAndArrival() {
        try {
            new SchedulingJob("P1", "p", 0, 0);
            assertFalse("burst 0 should be rejected", true);
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            new SchedulingJob("P1", "p", -1, 3);
            assertFalse("negative arrival should be rejected", true);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void jobPlaybackFieldsClampToBurst() {
        SchedulingJob job = job("P1", 0, 5);
        job.setPlayback(SchedulingJob.State.RUNNING, 99);
        assertEquals(5, job.getRemainingBurst());
        job.setPlayback(SchedulingJob.State.RUNNING, -4);
        assertEquals(0, job.getRemainingBurst());
        job.resetPlayback();
        assertEquals(5, job.getRemainingBurst());
        assertEquals(SchedulingJob.State.READY, job.getState());
    }
}
