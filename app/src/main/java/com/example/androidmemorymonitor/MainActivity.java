package com.example.androidmemorymonitor;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MemoryMonitor monitor = new MemoryMonitor();
    private TextView systemText;
    private TextView procText;
    private TextView appText;
    private TextView processesText;
    private TextView historyText;
    private ProgressBar usageProgress;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshScreen();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        systemText = findViewById(R.id.systemText);
        procText = findViewById(R.id.procText);
        appText = findViewById(R.id.appText);
        processesText = findViewById(R.id.processesText);
        historyText = findViewById(R.id.historyText);
        usageProgress = findViewById(R.id.usageProgress);
        refreshScreen();
        handler.postDelayed(refreshTask, 2000);
    }

    private void refreshScreen() {
        ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = monitor.readSystemMemory(activityManager);
        if (info == null) {
            systemText.setText("System memory information is unavailable.");
            usageProgress.setProgress(0);
        } else {
            long usedBytes = Math.max(0, info.totalMem - info.availMem);
            int percent = info.totalMem <= 0 ? 0 : (int) Math.min(100, usedBytes * 100.0 / info.totalMem);
            usageProgress.setProgress(percent);
            systemText.setText(String.format(java.util.Locale.US,
                    "Total RAM: %.2f GiB\nUsed RAM: %.2f GiB\nAvailable RAM: %.2f GiB\nUsage: %d%%\nStatus: %s",
                    toGiB(info.totalMem), toGiB(usedBytes), toGiB(info.availMem), percent,
                    info.lowMemory ? "LOW MEMORY" : "Normal"));
            monitor.addHistorySample(percent);
            historyText.setText(monitor.getHistoryText());
        }

        procText.setText(monitor.readProcMemInfo());
        ProcessInfo appInfo = monitor.readOwnProcess();
        appText.setText(appInfo == null ? "This app's memory information is unavailable."
                : appInfo.toDisplayString());
        processesText.setText(readVisibleProcesses(activityManager));
    }

    private String readVisibleProcesses(ActivityManager activityManager) {
        if (activityManager == null) {
            return "Process information is unavailable.";
        }
        List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
        if (processes == null || processes.isEmpty()) {
            return "Android did not provide a visible process list. Modern Android versions limit app process visibility.";
        }
        StringBuilder text = new StringBuilder();
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            int[] pids = new int[]{process.pid};
            try {
                android.os.Debug.MemoryInfo[] memory = activityManager.getProcessMemoryInfo(pids);
                int pssKb = memory != null && memory.length > 0 ? memory[0].getTotalPss() : -1;
                text.append(process.processName).append(" | PID ").append(process.pid)
                        .append(" | PSS: ").append(pssKb >= 0
                                ? String.format(java.util.Locale.US, "%.2f MiB", pssKb / 1024.0) : "unavailable")
                        .append('\n');
            } catch (RuntimeException exception) {
                text.append(process.processName).append(" | PID ").append(process.pid)
                        .append(" | PSS unavailable\n");
            }
        }
        text.append("\n\nAndroid limits visibility of unrelated apps. This list contains only processes the public API makes visible.");
        return text.toString().trim();
    }

    private double toGiB(long bytes) {
        return bytes / (1024.0 * 1024.0 * 1024.0);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshTask);
        super.onDestroy();
    }
}
