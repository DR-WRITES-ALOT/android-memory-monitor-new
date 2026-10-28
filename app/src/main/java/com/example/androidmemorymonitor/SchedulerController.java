package com.example.androidmemorymonitor;

import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plays a {@link SchedulerSimulator} result back over time so the UI can animate the
 * selected job while the "scheduler" executes, and tracks READY / RUNNING / COMPLETED
 * display states.
 *
 * <p>The simulation itself lives in {@link SchedulerSimulator} (pure Java, unit tested).
 * This controller only handles playback timing, Start / Pause / Resume / Reset, and
 * scheduling-work off the main thread. Everything it reports is simulated data.</p>
 */
public final class SchedulerController {

    /** Time between playback ticks in milliseconds. */
    public static final long TICK_MILLIS = 700;

    public interface Listener {
        /** Fired on every playback change (also immediately after start and reset). */
        void onPlaybackChanged(List<SchedulingJob> jobs, int revealedSlices,
                int currentTime, boolean finished, ScheduleResult result);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Listener listener;

    private List<SchedulingJob> jobs;
    private boolean roundRobin;
    private int quantum = 2;

    private ScheduleResult result;
    private int revealedSlices;
    private boolean playing;
    private boolean paused;
    private boolean finished;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!playing || paused) {
                return;
            }
            if (result == null) {
                return;
            }
            if (revealedSlices >= result.getSlices().size()) {
                playing = false;
                finished = true;
                updateJobStates();
                notifyChanged();
                return;
            }
            revealedSlices++;
            updateJobStates();
            if (revealedSlices >= result.getSlices().size()) {
                playing = false;
                finished = true;
                notifyChanged();
            } else {
                notifyChanged();
                mainHandler.postDelayed(this, TICK_MILLIS);
            }
        }
    };

    public SchedulerController(Listener listener) {
        this.listener = listener;
    }

    /** Replaces the job list (e.g. after demo workers changed) and resets playback. */
    public void setJobs(List<SchedulingJob> jobs) {
        this.jobs = jobs;
        reset();
    }

    public void setRoundRobin(boolean roundRobin) {
        this.roundRobin = roundRobin;
    }

    public void setQuantum(int quantum) {
        this.quantum = Math.max(1, quantum);
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isFinished() {
        return finished;
    }

    public ScheduleResult getResult() {
        return result;
    }

    /** Runs the simulation (off the main thread) and starts playback. */
    public void start() {
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        mainHandler.removeCallbacks(tickRunnable);
        finished = false;
        paused = false;
        revealedSlices = 0;
        final List<SchedulingJob> snapshot = jobs;
        final boolean rr = roundRobin;
        final int q = quantum;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final ScheduleResult computed = rr
                        ? SchedulerSimulator.simulateRoundRobin(snapshot, q)
                        : SchedulerSimulator.simulateFcfs(snapshot);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        result = computed;
                        for (SchedulingJob job : snapshot) {
                            job.resetPlayback();
                        }
                        playing = true;
                        updateJobStates();
                        notifyChanged();
                        mainHandler.postDelayed(tickRunnable, TICK_MILLIS);
                    }
                });
            }
        });
    }

    /** Pauses playback; the timeline keeps its position. */
    public void pause() {
        paused = true;
        notifyChanged();
    }

    /** Continues playback from the current position. */
    public void resume() {
        if (!paused || finished || result == null) {
            return;
        }
        paused = false;
        notifyChanged();
        mainHandler.postDelayed(tickRunnable, TICK_MILLIS);
    }

    /** Clears the timeline and job states back to the initial view. */
    public void reset() {
        mainHandler.removeCallbacks(tickRunnable);
        playing = false;
        paused = false;
        finished = false;
        revealedSlices = 0;
        result = null;
        if (jobs != null) {
            for (SchedulingJob job : jobs) {
                job.resetPlayback();
            }
        }
        notifyChanged();
    }

    /** Stops the executor. Call from {@code onDestroy()}. */
    public void destroy() {
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    // ----------------------------------------------------------------- internals

    private void updateJobStates() {
        if (jobs == null || result == null) {
            return;
        }
        int currentTime = currentTime();
        for (SchedulingJob job : jobs) {
            int executed = 0;
            boolean completed = false;
            boolean runningNow = false;
            for (int i = 0; i < revealedSlices; i++) {
                ScheduleResult.Slice slice = result.getSlices().get(i);
                if (!slice.jobId.equals(job.getJobId())) {
                    continue;
                }
                executed += slice.duration();
                if (slice.endTime <= currentTime && executed >= job.getBurstTime()) {
                    completed = true;
                }
                if (i == revealedSlices - 1) {
                    runningNow = true;
                }
            }
            if (completed && executed >= job.getBurstTime()) {
                job.setPlayback(SchedulingJob.State.COMPLETED, 0);
            } else if (runningNow && playing) {
                job.setPlayback(SchedulingJob.State.RUNNING,
                        job.getBurstTime() - executed);
            } else {
                job.setPlayback(SchedulingJob.State.READY,
                        job.getBurstTime() - executed);
            }
        }
    }

    private int currentTime() {
        if (result == null || revealedSlices == 0) {
            return 0;
        }
        return result.getSlices().get(revealedSlices - 1).endTime;
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onPlaybackChanged(jobs, revealedSlices, currentTime(), finished, result);
        }
    }
}
