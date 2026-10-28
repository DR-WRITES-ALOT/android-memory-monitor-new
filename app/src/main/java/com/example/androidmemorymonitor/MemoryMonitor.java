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
