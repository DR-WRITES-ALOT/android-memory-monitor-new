package com.example.androidmemorymonitor;

import android.app.ActivityManager;
import android.os.Debug;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MemoryMonitor {
    private final ArrayDeque<Integer> recentSamples = new ArrayDeque<>();

    /** Used RAM in bytes for one {@link ActivityManager.MemoryInfo} sample. */
    public static long usedBytes(ActivityManager.MemoryInfo info) {
        return Math.max(0, info.totalMem - info.availMem);
    }

    /** System RAM usage percent (0-100) for one sample. */
    public static int usagePercent(ActivityManager.MemoryInfo info) {
        if (info.totalMem <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.max(0, usedBytes(info) * 100.0 / info.totalMem));
    }

    public ActivityManager.MemoryInfo readSystemMemory(ActivityManager activityManager) {
        if (activityManager == null) {
            return null;
        }
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        try {
            activityManager.getMemoryInfo(info);
            return info;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public String readProcMemInfo() {
        String[] wantedFields = {"MemTotal", "MemFree", "MemAvailable", "Buffers", "Cached", "SwapTotal", "SwapFree"};
        List<String> foundLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                for (String field : wantedFields) {
                    if (line.startsWith(field + ":")) {
                        foundLines.add(line);
                        break;
                    }
                }
            }
        } catch (IOException | SecurityException exception) {
            return "/proc/meminfo is unavailable (read failed).";
        }
        if (foundLines.isEmpty()) {
            return "/proc/meminfo did not contain the requested fields.";
        }
        StringBuilder result = new StringBuilder();
        for (String line : foundLines) {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                try {
                    double mib = Long.parseLong(parts[1]) / 1024.0;
                    result.append(String.format(Locale.US, "%s: %.2f MiB\n",
                            parts[0].replace(":", ""), mib));
                } catch (NumberFormatException exception) {
                    result.append(line).append('\n');
                }
            }
        }
        return result.toString().trim();
    }

    public ProcessInfo readOwnProcess() {
        try {
            Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memoryInfo);
            return new ProcessInfo(android.os.Process.myPid(), memoryInfo);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public void addHistorySample(int percent) {
        recentSamples.addLast(percent);
        if (recentSamples.size() > 20) {
            recentSamples.removeFirst();
        }
    }

    /** Oldest-to-newest copy of the retained samples (at most 20). */
    public List<Integer> getHistorySamples() {
        return new ArrayList<>(recentSamples);
    }

    /**
     * Lists the processes Android exposes to this app, with PSS where the public API
     * provides it. Modern Android restricts visibility of unrelated apps; failures are
     * reported as text instead of fabricated values.
     */
    public String readVisibleProcesses(ActivityManager activityManager) {
        if (activityManager == null) {
            return "Process information is unavailable.";
        }
        List<ActivityManager.RunningAppProcessInfo> processes;
        try {
            processes = activityManager.getRunningAppProcesses();
        } catch (RuntimeException exception) {
            return "Android did not provide a visible process list.";
        }
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
                                ? String.format(Locale.US, "%.2f MiB", pssKb / 1024.0) : "unavailable")
                        .append('\n');
            } catch (RuntimeException exception) {
                text.append(process.processName).append(" | PID ").append(process.pid)
                        .append(" | PSS unavailable\n");
            }
        }
        text.append("\n\nAndroid limits visibility of unrelated apps. This list contains only processes the public API makes visible.");
        return text.toString().trim();
    }

    public String getHistoryText() {
        if (recentSamples.isEmpty()) {
            return "Waiting for samples.";
        }
        StringBuilder bars = new StringBuilder();
        StringBuilder values = new StringBuilder("Usage (%): ");
        final String levels = "▁▂▃▄▅▆▇█";
        for (Integer sample : recentSamples) {
            int level = Math.min(levels.length() - 1, sample * levels.length() / 101);
            bars.append(levels.charAt(level)).append(' ');
            values.append(sample).append("  ");
        }
        return "Oldest  " + bars + " Newest\n" + values.toString().trim();
    }
}
