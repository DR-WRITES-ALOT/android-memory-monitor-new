package com.example.androidmemorymonitor;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dashboard UI: live memory readings, demo-worker management (Feature B), automatic
 * cleanup at 80% RAM (Feature A) and the educational scheduling simulator (Feature C).
 *
 * <p>Live memory collection runs on a background executor so the two-second refresh never
 * blocks the main thread; every UI update happens on the main thread. Handlers, the
 * executor and all service bindings are released in {@code onDestroy()}.</p>
 */
public class MainActivity extends AppCompatActivity
        implements DuplicateProcessManager.Listener, SchedulerController.Listener {

    private static final int[] DEFAULT_BURSTS = {5, 3, 8, 6};
    private static final long DEFAULT_ALLOC_MIB = 4;
    private static final long MAX_ALLOC_MIB = 32;

    /** One async memory reading; produced off the main thread, applied on it. */
    private static final class MemorySnapshot {
        final ActivityManager.MemoryInfo systemInfo;
        final String kernelText;
        final ProcessInfo appInfo;
        final String processesText;

        MemorySnapshot(ActivityManager.MemoryInfo systemInfo, String kernelText,
                ProcessInfo appInfo, String processesText) {
            this.systemInfo = systemInfo;
            this.kernelText = kernelText;
            this.appInfo = appInfo;
            this.processesText = processesText;
        }
    }

    /** Before/after record for one manual or automatic cleanup cycle. */
    private static final class CleanupReport {
        final boolean automatic;
        final int workersRemoved;
        final int beforePercent;
        final long beforeUsedBytes;
        final long beforeTotalBytes;

        CleanupReport(boolean automatic, int workersRemoved, int beforePercent,
                long beforeUsedBytes, long beforeTotalBytes) {
            this.automatic = automatic;
            this.workersRemoved = workersRemoved;
            this.beforePercent = beforePercent;
            this.beforeUsedBytes = beforeUsedBytes;
            this.beforeTotalBytes = beforeTotalBytes;
        }
    }

    /** Row widgets for one simulated scheduling job. */
    private static final class JobRow {
        final TextView idText;
        final TextView labelText;
        final EditText arrivalInput;
        final EditText burstInput;
        final TextView remainingText;
        final TextView stateChip;

        JobRow(TextView idText, TextView labelText, EditText arrivalInput,
                EditText burstInput, TextView remainingText, TextView stateChip) {
            this.idText = idText;
            this.labelText = labelText;
            this.arrivalInput = arrivalInput;
            this.burstInput = burstInput;
            this.remainingText = remainingText;
            this.stateChip = stateChip;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService ioExecutor;
    private MemoryMonitor monitor;
    private DuplicateProcessManager workerManager;
    private MemoryThresholdManager thresholdManager;
    private SchedulerController schedulerController;

    /** Monotonic counter so stale async readings are dropped. */
    private int collectionGeneration;
    private int lastPercent;
    private long lastUsedBytes;
    private long lastTotalBytes;
    private boolean haveSystemSample;
    private CleanupReport pendingReport;
    private String lastFcfsSummary;
    private String lastRrSummary;
    private String jobSignature = "";

    // Views
    private TextView statusChip;
    private TextView updatedText;
    private TextView systemPercentText;
    private TextView systemText;
    private TextView warningBanner;
    private LinearProgressIndicator usageProgress;
    private TextView appText;
    private TextView procText;
    private SwitchCompat autoCleanupSwitch;
    private MaterialButton addWorkerButton;
    private MaterialButton removeAllButton;
    private MaterialButton cleanupNowButton;
    private EditText allocInput;
    private TextView workerTotalText;
    private LinearLayout workerListContainer;
    private TextView cleanupReportText;
    private MaterialButtonToggleGroup algorithmToggle;
    private EditText quantumInput;
    private TextView jobsHintText;
    private LinearLayout jobListContainer;
    private MaterialButton startButton;
    private MaterialButton pauseButton;
    private MaterialButton resetButton;
    private TextView ganttCaption;
    private GanttChartView ganttView;
    private TextView resultsText;
    private TextView comparisonText;
    private TextView explanationText;
    private SparklineView sparkline;
    private TextView historyText;
    private TextView processesText;

    private final List<JobRow> jobRows = new ArrayList<>();

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            collectMemoryAsync();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ioExecutor = Executors.newSingleThreadExecutor();
        monitor = new MemoryMonitor();
        thresholdManager = new MemoryThresholdManager();
        workerManager = new DuplicateProcessManager(this, this);
        schedulerController = new SchedulerController(this);

        bindViews();
        wireControls();

        updateWorkerRows();
        rebuildJobRowsIfNeeded();
        collectMemoryAsync();
        handler.postDelayed(refreshTask, 2000);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        schedulerController.destroy();
        workerManager.close();
        ioExecutor.shutdown();
        super.onDestroy();
    }

    // ----------------------------------------------------------------- setup

    private void bindViews() {
        statusChip = findViewById(R.id.statusChip);
        updatedText = findViewById(R.id.updatedText);
        systemPercentText = findViewById(R.id.systemPercentText);
        systemText = findViewById(R.id.systemText);
        warningBanner = findViewById(R.id.warningBanner);
        usageProgress = findViewById(R.id.usageProgress);
        appText = findViewById(R.id.appText);
        procText = findViewById(R.id.procText);
        autoCleanupSwitch = findViewById(R.id.autoCleanupSwitch);
        addWorkerButton = findViewById(R.id.addWorkerButton);
        removeAllButton = findViewById(R.id.removeAllButton);
        cleanupNowButton = findViewById(R.id.cleanupNowButton);
        allocInput = findViewById(R.id.allocInput);
        workerTotalText = findViewById(R.id.workerTotalText);
        workerListContainer = findViewById(R.id.workerListContainer);
        cleanupReportText = findViewById(R.id.cleanupReportText);
        algorithmToggle = findViewById(R.id.algorithmToggle);
        quantumInput = findViewById(R.id.quantumInput);
        jobsHintText = findViewById(R.id.jobsHintText);
        jobListContainer = findViewById(R.id.jobListContainer);
        startButton = findViewById(R.id.startButton);
        pauseButton = findViewById(R.id.pauseButton);
        resetButton = findViewById(R.id.resetButton);
        ganttCaption = findViewById(R.id.ganttCaption);
        ganttView = findViewById(R.id.ganttView);
        resultsText = findViewById(R.id.resultsText);
        comparisonText = findViewById(R.id.comparisonText);
        explanationText = findViewById(R.id.explanationText);
        sparkline = findViewById(R.id.sparkline);
        historyText = findViewById(R.id.historyText);
        processesText = findViewById(R.id.processesText);
    }

    private void wireControls() {
        addWorkerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addWorker();
            }
        });
        removeAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performCleanup(false);
            }
        });
        cleanupNowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performCleanup(false);
            }
        });
        algorithmToggle.addOnButtonCheckedListener(
                new MaterialButtonToggleGroup.OnButtonCheckedListener() {
                    @Override
                    public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId,
                            boolean isChecked) {
                        if (!isChecked) {
                            return;
                        }
                        boolean rr = checkedId == R.id.algoRr;
                        schedulerController.setRoundRobin(rr);
                        quantumInput.setEnabled(rr);
                        explanationText.setText(rr
                                ? R.string.explain_rr : R.string.explain_fcfs);
                    }
                });
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startSimulation();
            }
        });
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (schedulerController.isPaused()) {
                    schedulerController.resume();
                } else {
                    schedulerController.pause();
                }
            }
        });
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                schedulerController.reset();
            }
        });
    }

    // ---------------------------------------------------- memory collection

    private void collectMemoryAsync() {
        final int generation = ++collectionGeneration;
        final Context appContext = getApplicationContext();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                ActivityManager activityManager =
                        (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo info = monitor.readSystemMemory(activityManager);
                MemorySnapshot snapshot = new MemorySnapshot(
                        info,
                        monitor.readProcMemInfo(),
                        monitor.readOwnProcess(),
                        monitor.readVisibleProcesses(activityManager));
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (generation != collectionGeneration || isFinishing() || isDestroyed()) {
                            return;
                        }
                        applySnapshot(snapshot);
                    }
                });
            }
        });
    }

    private void applySnapshot(MemorySnapshot snapshot) {
        updatedText.setText(getString(R.string.updated_at,
                new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())));

        if (snapshot.systemInfo == null) {
            systemText.setText(R.string.system_unavailable);
            systemPercentText.setText("--");
            usageProgress.setProgressCompat(0, true);
        } else {
            ActivityManager.MemoryInfo info = snapshot.systemInfo;
            lastPercent = MemoryMonitor.usagePercent(info);
            lastUsedBytes = MemoryMonitor.usedBytes(info);
            lastTotalBytes = info.totalMem;
            haveSystemSample = true;

            systemPercentText.setText(String.format(Locale.US, "%d%% used", lastPercent));
            systemText.setText(getString(R.string.system_details_format,
                    toGiB(lastUsedBytes), toGiB(lastTotalBytes), toGiB(info.availMem),
                    info.lowMemory ? getString(R.string.low_memory_yes)
                            : getString(R.string.low_memory_no)));
            usageProgress.setProgressCompat(lastPercent, true);
            applyPressureStyling(lastPercent, info.lowMemory);

            monitor.addHistorySample(lastPercent);
            historyText.setText(monitor.getHistoryText());
            sparkline.setSamples(monitor.getHistorySamples());
        }

        procText.setText(snapshot.kernelText);
        appText.setText(snapshot.appInfo == null
                ? getString(R.string.app_unavailable) : snapshot.appInfo.toDisplayString());
        processesText.setText(snapshot.processesText);

        updateWorkerRows();
        workerManager.requestStats();
        rebuildJobRowsIfNeeded();
        // Capture the "after" numbers of a cleanup that ran on a previous sample, then
        // evaluate the threshold for this sample (which may start a new cycle).
        captureCleanupAfter();
        runThresholdLogic();
    }

    private void applyPressureStyling(int percent, boolean lowMemory) {
        boolean warning = MemoryThresholdManager.isWarning(percent);
        usageProgress.setIndicatorColor(getResources().getColor(
                warning ? R.color.warning_red : R.color.primary, getTheme()));
        if (warning || lowMemory) {
            statusChip.setBackgroundResource(R.drawable.bg_chip_red);
            statusChip.setTextColor(getResources().getColor(R.color.warning_red, getTheme()));
            statusChip.setText(lowMemory ? R.string.status_low_memory : R.string.status_high_pressure);
        } else {
            statusChip.setBackgroundResource(R.drawable.bg_chip_green);
            statusChip.setTextColor(getResources().getColor(R.color.success_green, getTheme()));
            statusChip.setText(R.string.status_normal);
        }
        if (warning) {
            warningBanner.setVisibility(View.VISIBLE);
            warningBanner.setText(getString(autoCleanupSwitch.isChecked()
                    ? R.string.ram_warning_message : R.string.ram_warning_no_auto, percent));
        } else {
            warningBanner.setVisibility(View.GONE);
        }
    }

    private void runThresholdLogic() {
        if (!haveSystemSample) {
            return;
        }
        boolean hasWorkers = workerManager.getAliveCount() > 0;
        MemoryThresholdManager.Action action = thresholdManager.update(
                lastPercent, autoCleanupSwitch.isChecked(), hasWorkers);
        switch (action) {
            case TRIGGER_CLEANUP:
                performCleanup(true);
                break;
            case TRIGGER_CLEANUP_NO_WORKERS:
                showReport(getString(R.string.cleanup_no_workers));
                break;
            case REARM:
            case NONE:
            default:
                break;
        }
    }

    // ------------------------------------------------------------ cleanup

    /**
     * Stops every demo worker process owned by this app (and nothing else) and records
     * RAM usage before and after. The "after" numbers are captured on the next refresh
     * sample, two seconds later, when the worker processes are gone.
     */
    private void performCleanup(boolean automatic) {
        int removed = workerManager.removeAllWorkers();
        if (removed == 0) {
            showReport(getString(automatic
                    ? R.string.cleanup_no_workers : R.string.cleanup_manual_nothing));
            return;
        }
        pendingReport = new CleanupReport(automatic, removed, lastPercent,
                lastUsedBytes, lastTotalBytes);
        showReport(getString(R.string.cleanup_waiting));
    }

    private void captureCleanupAfter() {
        if (pendingReport == null || !haveSystemSample) {
            return;
        }
        CleanupReport report = pendingReport;
        pendingReport = null;
        showReport(MemoryThresholdManager.formatCleanupReport(
                report.automatic, report.workersRemoved, report.beforePercent,
                report.beforeUsedBytes, report.beforeTotalBytes, lastPercent, lastUsedBytes));
    }

    private void showReport(String message) {
        cleanupReportText.setVisibility(View.VISIBLE);
        cleanupReportText.setText(message);
    }

    // ------------------------------------------------------------- workers

    private void addWorker() {
        long allocBytes = parseOr(allocInput, DEFAULT_ALLOC_MIB, 1, MAX_ALLOC_MIB)
                * 1024L * 1024L;
        WorkerProcess worker = workerManager.addWorker(allocBytes);
        if (worker == null) {
            Toast.makeText(this, R.string.workers_max_reached, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onWorkersChanged() {
        updateWorkerRows();
        rebuildJobRowsIfNeeded();
    }

    @Override
    public void onWorkerStatsUpdated() {
        updateWorkerRows();
    }

    private void updateWorkerRows() {
        List<WorkerProcess> workers = workerManager.getWorkers();
        workerListContainer.removeAllViews();
        addWorkerButton.setEnabled(workers.size() < DuplicateProcessManager.MAX_WORKERS);

        if (workers.isEmpty()) {
            workerTotalText.setText(R.string.workers_empty);
            return;
        }

        LayoutInflater inflater = getLayoutInflater();
        long totalPssKb = workerManager.getTotalPssKb();
        long allocatedBytes = 0;
        int alive = 0;
        for (WorkerProcess worker : workers) {
            if (worker.isAlive()) {
                alive++;
                allocatedBytes += worker.getAllocatedBytes();
            }
            View row = inflater.inflate(R.layout.item_worker, workerListContainer, false);
            TextView name = row.findViewById(R.id.workerName);
            TextView pid = row.findViewById(R.id.workerPid);
            TextView pss = row.findViewById(R.id.workerPss);
            TextView dirty = row.findViewById(R.id.workerDirty);
            TextView shareText = row.findViewById(R.id.workerShareText);
            android.widget.ProgressBar shareBar = row.findViewById(R.id.workerShareBar);
            TextView statusView = row.findViewById(R.id.workerStatusChip);
            View remove = row.findViewById(R.id.workerRemoveButton);

            name.setText(worker.getWorkerName());
            pid.setText(String.format(Locale.US, "PID %s · %s",
                    worker.getPid() > 0 ? String.valueOf(worker.getPid()) : "?",
                    worker.getProcessName()));
            pss.setText("PSS " + worker.formatPss());
            dirty.setText("dirty " + worker.formatPrivateDirty());
            if (worker.getPssKb() >= 0 && totalPssKb > 0) {
                int share = (int) Math.min(100, worker.getPssKb() * 100L / totalPssKb);
                shareText.setText(getString(R.string.worker_share_format, share));
                shareBar.setProgress(share);
            } else {
                shareText.setText("--");
                shareBar.setProgress(0);
            }
            bindWorkerStatus(statusView, worker.getStatus());
            remove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    workerManager.removeWorker(worker.getSlotIndex());
                }
            });
            workerListContainer.addView(row);
        }

        if (totalPssKb >= 0) {
            workerTotalText.setText(getString(R.string.worker_total_format, alive,
                    formatMibFromKb(totalPssKb),
                    String.format(Locale.US, "%.2f MiB", allocatedBytes / (1024.0 * 1024.0))));
        } else {
            workerTotalText.setText(getString(R.string.worker_total_unavailable, alive));
        }
    }

    private void bindWorkerStatus(TextView statusView, WorkerProcess.Status status) {
        switch (status) {
            case RUNNING:
                statusView.setBackgroundResource(R.drawable.bg_chip_green);
                statusView.setTextColor(getResources().getColor(R.color.success_green, getTheme()));
                statusView.setText(R.string.worker_status_running);
                break;
            case STOPPED:
                statusView.setBackgroundResource(R.drawable.bg_chip_grey);
                statusView.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
                statusView.setText(R.string.worker_status_stopped);
                break;
            case KILLED:
                statusView.setBackgroundResource(R.drawable.bg_chip_red);
                statusView.setTextColor(getResources().getColor(R.color.warning_red, getTheme()));
                statusView.setText(R.string.worker_status_killed);
                break;
            case STARTING:
            default:
                statusView.setBackgroundResource(R.drawable.bg_chip_blue);
                statusView.setTextColor(getResources().getColor(R.color.info_blue, getTheme()));
                statusView.setText(R.string.worker_status_starting);
                break;
        }
    }

    // ------------------------------------------------------------ scheduler

    private void rebuildJobRowsIfNeeded() {
        List<WorkerProcess> aliveWorkers = new ArrayList<>();
        StringBuilder signature = new StringBuilder();
        for (WorkerProcess worker : workerManager.getWorkers()) {
            signature.append(worker.getSlotIndex()).append(':')
                    .append(worker.isAlive() ? 1 : 0).append(':')
                    .append(worker.getPid()).append(';');
            if (worker.isAlive()) {
                aliveWorkers.add(worker);
            }
        }
        if (signature.toString().equals(jobSignature)) {
            updateJobLabels(aliveWorkers);
            return;
        }
        jobSignature = signature.toString();

        jobListContainer.removeAllViews();
        jobRows.clear();
        if (aliveWorkers.isEmpty()) {
            jobsHintText.setVisibility(View.VISIBLE);
            startButton.setEnabled(false);
            return;
        }
        jobsHintText.setVisibility(View.GONE);
        startButton.setEnabled(true);

        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < aliveWorkers.size(); i++) {
            WorkerProcess worker = aliveWorkers.get(i);
            View row = inflater.inflate(R.layout.item_job, jobListContainer, false);
            TextView idText = row.findViewById(R.id.jobIdText);
            TextView label = row.findViewById(R.id.jobLabel);
            EditText arrival = row.findViewById(R.id.jobArrivalInput);
            EditText burst = row.findViewById(R.id.jobBurstInput);
            TextView remaining = row.findViewById(R.id.jobRemainingText);
            TextView stateChip = row.findViewById(R.id.jobStateChip);

            idText.setText("P" + (i + 1));
            label.setText(jobLabelFor(worker));
            arrival.setText(String.valueOf(i));
            burst.setText(String.valueOf(DEFAULT_BURSTS[i % DEFAULT_BURSTS.length]));
            remaining.setText(getString(R.string.remaining_format, DEFAULT_BURSTS[i % DEFAULT_BURSTS.length]));
            stateChip.setText(R.string.state_ready);

            jobRows.add(new JobRow(idText, label, arrival, burst, remaining, stateChip));
            jobListContainer.addView(row);
        }
    }

    private void updateJobLabels(List<WorkerProcess> workers) {
        for (int i = 0; i < jobRows.size() && i < workers.size(); i++) {
            jobRows.get(i).labelText.setText(jobLabelFor(workers.get(i)));
        }
    }

    private String jobLabelFor(WorkerProcess worker) {
        return worker.getPid() > 0
                ? worker.getWorkerName() + " (PID " + worker.getPid() + ")"
                : worker.getWorkerName() + " (" + worker.getProcessName() + ")";
    }

    private void startSimulation() {
        if (jobRows.isEmpty()) {
            return;
        }
        List<SchedulingJob> jobs = new ArrayList<>();
        for (int i = 0; i < jobRows.size(); i++) {
            JobRow row = jobRows.get(i);
            int arrival = parseOr(row.arrivalInput, i, 0, 100);
            int burst = parseOr(row.burstInput, DEFAULT_BURSTS[i % DEFAULT_BURSTS.length], 1, 50);
            jobs.add(new SchedulingJob("P" + (i + 1), row.labelText.getText().toString(),
                    arrival, burst));
        }
        schedulerController.setJobs(jobs);
        schedulerController.setRoundRobin(
                algorithmToggle.getCheckedButtonId() == R.id.algoRr);
        schedulerController.setQuantum(parseOr(quantumInput, 2, 1, 10));
        schedulerController.start();
    }

    @Override
    public void onPlaybackChanged(List<SchedulingJob> jobs, int revealedSlices,
            int currentTime, boolean finished, ScheduleResult result) {
        if (result == null) {
            ganttView.setTimeline(new ArrayList<ScheduleResult.Slice>(), 0);
            ganttCaption.setText(R.string.gantt_caption_idle);
            resultsText.setText(R.string.results_idle);
        } else {
            ganttView.setTimeline(result.getSlices(), revealedSlices);
            String phase = finished ? getString(R.string.playback_finished)
                    : schedulerController.isPaused() ? getString(R.string.playback_paused)
                    : getString(R.string.playback_running);
            ganttCaption.setText(getString(R.string.gantt_caption_format, currentTime, phase));
            if (finished) {
                showResults(result);
            }
        }

        for (int i = 0; i < jobRows.size(); i++) {
            JobRow row = jobRows.get(i);
            SchedulingJob job = jobs != null && i < jobs.size() ? jobs.get(i) : null;
            if (job == null) {
                continue;
            }
            row.remainingText.setText(getString(R.string.remaining_format, job.getRemainingBurst()));
            switch (job.getState()) {
                case RUNNING:
                    row.stateChip.setText(R.string.state_running);
                    row.stateChip.setBackgroundResource(R.drawable.bg_chip_green);
                    row.stateChip.setTextColor(
                            getResources().getColor(R.color.success_green, getTheme()));
                    break;
                case COMPLETED:
                    row.stateChip.setText(R.string.state_completed);
                    row.stateChip.setBackgroundResource(R.drawable.bg_chip_grey);
                    row.stateChip.setTextColor(
                            getResources().getColor(R.color.text_secondary, getTheme()));
                    break;
                case READY:
                default:
                    row.stateChip.setText(R.string.state_ready);
                    row.stateChip.setBackgroundResource(R.drawable.bg_chip_blue);
                    row.stateChip.setTextColor(
                            getResources().getColor(R.color.info_blue, getTheme()));
                    break;
            }
        }

        boolean active = result != null && !finished;
        pauseButton.setEnabled(active);
        pauseButton.setText(schedulerController.isPaused()
                ? R.string.resume_simulation : R.string.pause_simulation);
        startButton.setEnabled(!jobRows.isEmpty());
    }

    private void showResults(ScheduleResult result) {
        StringBuilder text = new StringBuilder();
        text.append(getString(R.string.results_header_format, result.getAlgorithmLabel())).append('\n');
        for (ScheduleResult.JobTiming timing : result.getTimings()) {
            text.append(getString(R.string.results_row_format, timing.jobId,
                    timing.arrivalTime, timing.burstTime,
                    timing.waitingTime, timing.turnaroundTime)).append('\n');
        }
        text.append(getString(R.string.results_averages_format,
                result.getAverageWaitingTime(), result.getAverageTurnaroundTime()));
        resultsText.setText(text.toString().trim());

        String summary = result.getSummaryLine();
        if (result.getAlgorithmLabel().startsWith("FCFS")) {
            lastFcfsSummary = summary;
        } else {
            lastRrSummary = summary;
        }
        if (lastFcfsSummary != null && lastRrSummary != null) {
            comparisonText.setText(getString(R.string.comparison_format,
                    lastFcfsSummary, lastRrSummary));
        } else {
            comparisonText.setText(R.string.comparison_placeholder);
        }
    }

    // -------------------------------------------------------------- helpers

    private int parseOr(EditText input, long fallback, long min, long max) {
        try {
            long value = Long.parseLong(input.getText().toString().trim());
            return (int) Math.max(min, Math.min(max, value));
        } catch (NumberFormatException exception) {
            return (int) Math.max(min, Math.min(max, fallback));
        }
    }

    private String formatMibFromKb(long kib) {
        return String.format(Locale.US, "%.2f MiB", kib / 1024.0);
    }

    private double toGiB(long bytes) {
        return bytes / (1024.0 * 1024.0 * 1024.0);
    }
}
