package com.example.androidmemorymonitor;

/**
 * One CPU-scheduling job for the educational scheduler simulation.
 *
 * <p>A job mirrors a demo worker process (name/PID) but its arrival time and CPU burst are
 * simulated values for the classroom demonstration. The model is pure Java so the
 * scheduling logic can be unit tested on the JVM.</p>
 */
public final class SchedulingJob {

    /** Display states used while the simulation plays back. */
    public enum State {
        /** Not started yet (pre-arrival) in the current playback position. */
        READY,
        /** The simulated CPU is executing this job in the current slice. */
        RUNNING,
        /** The job has finished its whole CPU burst in the simulation. */
        COMPLETED
    }

    private final String jobId;
    private final String processLabel;
    private final int arrivalTime;
    private final int burstTime;

    // Playback-only display fields; scheduling math never depends on them.
    private State state = State.READY;
    private int remainingBurst;

    public SchedulingJob(String jobId, String processLabel, int arrivalTime, int burstTime) {
        if (jobId == null || jobId.isEmpty()) {
            throw new IllegalArgumentException("jobId must not be empty");
        }
        if (arrivalTime < 0) {
            throw new IllegalArgumentException("arrivalTime must be >= 0");
        }
        if (burstTime <= 0) {
            throw new IllegalArgumentException("burstTime must be > 0");
        }
        this.jobId = jobId;
        this.processLabel = processLabel == null ? jobId : processLabel;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.remainingBurst = burstTime;
    }

    public String getJobId() {
        return jobId;
    }

    /** Human readable label, e.g. "Worker A (PID 1234)" or a plain placeholder. */
    public String getProcessLabel() {
        return processLabel;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public int getBurstTime() {
        return burstTime;
    }

    public State getState() {
        return state;
    }

    public int getRemainingBurst() {
        return remainingBurst;
    }

    /** Used by the UI playback; not part of the scheduling computation. */
    public void setPlayback(State state, int remainingBurst) {
        this.state = state;
        this.remainingBurst = Math.max(0, Math.min(burstTime, remainingBurst));
    }

    /** Resets the playback display fields to their initial values. */
    public void resetPlayback() {
        this.state = State.READY;
        this.remainingBurst = burstTime;
    }

    @Override
    public String toString() {
        return jobId + "(arrival=" + arrivalTime + ", burst=" + burstTime + ")";
    }
}
