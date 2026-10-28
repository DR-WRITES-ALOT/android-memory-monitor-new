package com.example.androidmemorymonitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Educational CPU-scheduling simulator with First Come, First Served (FCFS) and Round
 * Robin (RR) algorithms.
 *
 * <p><b>Important:</b> this is an explicit classroom simulation for the demo worker jobs.
 * Ordinary Android apps cannot observe the Linux kernel's real scheduling decisions
 * through public APIs, so nothing here is presented as a measurement of the real
 * scheduler. Simulated time units are abstract ticks, not milliseconds of real CPU
 * time.</p>
 *
 * <p>The class is pure Java and side-effect free: the input jobs are never mutated, so the
 * same job list can be replayed with different algorithms for comparison.</p>
 */
public final class SchedulerSimulator {

    /** Slice label used when the simulated CPU has nothing ready to run. */
    public static final String IDLE_LABEL = "IDLE";

    private SchedulerSimulator() {
    }

    /**
     * First Come, First Served: jobs run to completion in arrival order (input order is
     * used as tie-breaker). Non-preemptive.
     */
    public static ScheduleResult simulateFcfs(List<SchedulingJob> jobs) {
        List<SchedulingJob> ordered = sortedByArrival(jobs);
        List<ScheduleResult.Slice> slices = new ArrayList<>();
        List<ScheduleResult.JobTiming> timings = new ArrayList<>();
        int time = 0;
        for (SchedulingJob job : ordered) {
            if (time < job.getArrivalTime()) {
                slices.add(new ScheduleResult.Slice(IDLE_LABEL, time, job.getArrivalTime()));
                time = job.getArrivalTime();
            }
            int completion = time + job.getBurstTime();
            slices.add(new ScheduleResult.Slice(job.getJobId(), time, completion));
            timings.add(makeTiming(job, completion));
            time = completion;
        }
        return new ScheduleResult("FCFS", slices, timings);
    }

    /**
     * Round Robin with the given time quantum. At each scheduling point every job that has
     * arrived joins the ready queue; the queue head runs for up to one quantum and is
     * re-queued at the back if it still has remaining burst (after jobs that arrived
     * during its slice).
     */
    public static ScheduleResult simulateRoundRobin(List<SchedulingJob> jobs, int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("quantum must be > 0");
        }
        List<SchedulingJob> ordered = sortedByArrival(jobs);
        int n = ordered.size();
        int[] remaining = new int[n];
        int[] completion = new int[n];
        for (int i = 0; i < n; i++) {
            remaining[i] = ordered.get(i).getBurstTime();
        }

        List<ScheduleResult.Slice> slices = new ArrayList<>();
        List<Integer> ready = new ArrayList<>();
        int time = 0;
        int next = 0;          // next job index to admit
        int finished = 0;

        while (finished < n) {
            // Admit all arrivals that are due at the current time.
            while (next < n && ordered.get(next).getArrivalTime() <= time) {
                ready.add(next);
                next++;
            }
            if (ready.isEmpty()) {
                // Nothing ready: idle until the next arrival.
                int idleUntil = ordered.get(next).getArrivalTime();
                slices.add(new ScheduleResult.Slice(IDLE_LABEL, time, idleUntil));
                time = idleUntil;
                continue;
            }
            int index = ready.remove(0);
            SchedulingJob job = ordered.get(index);
            int run = Math.min(remaining[index], quantum);
            int end = time + run;
            slices.add(new ScheduleResult.Slice(job.getJobId(), time, end));
            remaining[index] -= run;
            time = end;
            if (remaining[index] == 0) {
                completion[index] = time;
                finished++;
            }
            // Arrivals during the slice join before the preempted job is re-queued.
            while (next < n && ordered.get(next).getArrivalTime() <= time) {
                ready.add(next);
                next++;
            }
            if (remaining[index] > 0) {
                ready.add(index);
            }
        }

        List<ScheduleResult.JobTiming> timings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            timings.add(makeTiming(ordered.get(i), completion[i]));
        }
        return new ScheduleResult("Round Robin (q=" + quantum + ")", slices, timings);
    }

    private static ScheduleResult.JobTiming makeTiming(SchedulingJob job, int completionTime) {
        int turnaround = completionTime - job.getArrivalTime();
        int waiting = turnaround - job.getBurstTime();
        return new ScheduleResult.JobTiming(job.getJobId(), job.getArrivalTime(),
                job.getBurstTime(), completionTime, turnaround, waiting);
    }

    private static List<SchedulingJob> sortedByArrival(List<SchedulingJob> jobs) {
        if (jobs == null) {
            throw new IllegalArgumentException("jobs must not be null");
        }
        List<SchedulingJob> copy = new ArrayList<>(jobs);
        copy.sort(Comparator.comparingInt(SchedulingJob::getArrivalTime));
        return copy;
    }
}
