package com.example.androidmemorymonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Result of one scheduler simulation run: the Gantt-style execution timeline plus the
 * per-job and average waiting/turnaround times.
 */
public final class ScheduleResult {

    /** One contiguous block of simulated CPU time. */
    public static final class Slice {
        /** Job id that ran, or {@code IDLE_LABEL} when the simulated CPU was idle. */
        public final String jobId;
        public final int startTime;
        public final int endTime;

        public Slice(String jobId, int startTime, int endTime) {
            this.jobId = jobId;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public int duration() {
            return endTime - startTime;
        }

        public boolean isIdle() {
            return SchedulerSimulator.IDLE_LABEL.equals(jobId);
        }
    }

    /** Per-job timing result. Waiting time = turnaround time - burst time. */
    public static final class JobTiming {
        public final String jobId;
        public final int arrivalTime;
        public final int burstTime;
        public final int completionTime;
        public final int turnaroundTime;
        public final int waitingTime;

        public JobTiming(String jobId, int arrivalTime, int burstTime,
                int completionTime, int turnaroundTime, int waitingTime) {
            this.jobId = jobId;
            this.arrivalTime = arrivalTime;
            this.burstTime = burstTime;
            this.completionTime = completionTime;
            this.turnaroundTime = turnaroundTime;
            this.waitingTime = waitingTime;
        }
    }

    private final String algorithmLabel;
    private final List<Slice> slices;
    private final Map<String, JobTiming> timings;

    public ScheduleResult(String algorithmLabel, List<Slice> slices, List<JobTiming> timingList) {
        this.algorithmLabel = algorithmLabel;
        this.slices = Collections.unmodifiableList(new ArrayList<>(slices));
        Map<String, JobTiming> map = new LinkedHashMap<>();
        for (JobTiming timing : timingList) {
            map.put(timing.jobId, timing);
        }
        this.timings = Collections.unmodifiableMap(map);
    }

    public String getAlgorithmLabel() {
        return algorithmLabel;
    }

    public List<Slice> getSlices() {
        return slices;
    }

    /** Timing rows in the order the jobs were supplied to the simulation. */
    public List<JobTiming> getTimings() {
        return new ArrayList<>(timings.values());
    }

    public JobTiming getTiming(String jobId) {
        return timings.get(jobId);
    }

    public int getTotalTime() {
        return slices.isEmpty() ? 0 : slices.get(slices.size() - 1).endTime;
    }

    public double getAverageWaitingTime() {
        if (timings.isEmpty()) {
            return 0.0;
        }
        long sum = 0;
        for (JobTiming timing : timings.values()) {
            sum += timing.waitingTime;
        }
        return sum / (double) timings.size();
    }

    public double getAverageTurnaroundTime() {
        if (timings.isEmpty()) {
            return 0.0;
        }
        long sum = 0;
        for (JobTiming timing : timings.values()) {
            sum += timing.turnaroundTime;
        }
        return sum / (double) timings.size();
    }

    /** Compact result line used in the comparison strip and tests. */
    public String getSummaryLine() {
        return String.format(Locale.US, "%s: avg waiting %.2f, avg turnaround %.2f (finish at %d)",
                algorithmLabel, getAverageWaitingTime(), getAverageTurnaroundTime(), getTotalTime());
    }
}
