package com.example.androidmemorymonitor;

import android.os.Debug;

/** Memory measurements for this application process. */
public class ProcessInfo {
    public final int pid;
    public final int pssKb;
    public final int privateDirtyKb;
    public final long nativeHeapBytes;
    public final long javaHeapBytes;

    public ProcessInfo(int pid, Debug.MemoryInfo memoryInfo) {
        this.pid = pid;
        pssKb = memoryInfo.getTotalPss();
        privateDirtyKb = memoryInfo.getTotalPrivateDirty();
        nativeHeapBytes = android.os.Debug.getNativeHeapAllocatedSize();
        javaHeapBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    public String toDisplayString() {
        return String.format(java.util.Locale.US,
                "PID: %d\nPSS: %.2f MiB\nPrivate dirty memory: %.2f MiB\nNative heap allocated: %.2f MiB\nJava heap used: %.2f MiB",
                pid, pssKb / 1024.0, privateDirtyKb / 1024.0,
                toMiB(nativeHeapBytes), toMiB(javaHeapBytes));
    }

    private double toMiB(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }
}
