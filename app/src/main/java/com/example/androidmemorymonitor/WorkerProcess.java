package com.example.androidmemorymonitor;

import java.util.Locale;

/**
 * Model for one demo worker process owned by this application.
 *
 * <p>Each worker runs in its own Android process (see the {@code android:process}
 * declarations in {@code AndroidManifest.xml}) so the dashboard can show how memory is
 * distributed across processes. Only processes of this app are ever managed; the app
 * never touches unrelated third-party processes.</p>
 */
public final class WorkerProcess {

    /** Lifecycle state shown in the worker table. */
    public enum Status {
        /** Service is created; first stats reply not received yet. */
        STARTING,
        /** Worker process is alive and answering status requests. */
        RUNNING,
        /** Stopped deliberately (manual or automatic cleanup). */
        STOPPED,
        /** The process died (killed by Android or crashed) without a stop request. */
        KILLED
    }

    private final int slotIndex;
    private final String workerName;
    private final String processName;
    private final long allocatedBytes;

    private int pid = -1;
    private int pssKb = -1;
    private int privateDirtyKb = -1;
    private Status status = Status.STARTING;

    public WorkerProcess(int slotIndex, String workerName, String processName, long allocatedBytes) {
        this.slotIndex = slotIndex;
        this.workerName = workerName;
        this.processName = processName;
        this.allocatedBytes = allocatedBytes;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public String getWorkerName() {
        return workerName;
    }

    /** The private process name, e.g. {@code :worker_a}. */
    public String getProcessName() {
        return processName;
    }

    /** Memory block the worker allocated for the demonstration, in bytes. */
    public long getAllocatedBytes() {
        return allocatedBytes;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    /** Proportional set size in KiB, or -1 when unavailable. */
    public int getPssKb() {
        return pssKb;
    }

    public void setPssKb(int pssKb) {
        this.pssKb = pssKb;
    }

    /** Private dirty memory in KiB, or -1 when unavailable. */
    public int getPrivateDirtyKb() {
        return privateDirtyKb;
    }

    public void setPrivateDirtyKb(int privateDirtyKb) {
        this.privateDirtyKb = privateDirtyKb;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isAlive() {
        return status == Status.STARTING || status == Status.RUNNING;
    }

    public String formatPss() {
        return pssKb >= 0 ? String.format(Locale.US, "%.2f MiB", pssKb / 1024.0) : "unavailable";
    }

    public String formatPrivateDirty() {
        return privateDirtyKb >= 0
                ? String.format(Locale.US, "%.2f MiB", privateDirtyKb / 1024.0) : "unavailable";
    }

    public String formatAllocated() {
        return String.format(Locale.US, "%.2f MiB", allocatedBytes / (1024.0 * 1024.0));
    }

    /** Single-line summary used in logs and the detailed process section. */
    public String toSummaryLine() {
        return String.format(Locale.US, "%s (%s) | PID %s | PSS %s | private dirty %s | status %s",
                workerName, processName,
                pid > 0 ? String.valueOf(pid) : "?",
                formatPss(), formatPrivateDirty(), status);
    }
}
