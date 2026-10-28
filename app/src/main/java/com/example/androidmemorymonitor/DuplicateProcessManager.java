package com.example.androidmemorymonitor;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the lifecycle of the app's demo worker processes (Feature B) and the automatic
 * cleanup path (Feature A).
 *
 * <p>Workers are ordinary Android Services of this application, each declared with its own
 * {@code android:process} name, so they are real separate processes with separate PIDs and
 * separate memory footprints. The manager binds to them (lifecycle-safe) and talks to them
 * over a {@link Messenger}. It only ever stops these app-owned workers — Android forbids
 * ordinary apps from uninstalling or force-stopping unrelated third-party apps, and this
 * class does not attempt it.</p>
 */
public final class DuplicateProcessManager {

    /** Fixed worker limit so the demo cannot exhaust device resources. */
    public static final int MAX_WORKERS = 4;

    private static final Class<?>[] SLOT_SERVICES = {
            WorkerAService.class, WorkerBService.class, WorkerCService.class, WorkerDService.class
    };
    private static final String[] SLOT_PROCESS_NAMES = {
            ":worker_a", ":worker_b", ":worker_c", ":worker_d"
    };
    private static final String[] SLOT_LABELS = {
            "Worker A", "Worker B", "Worker C", "Worker D"
    };

    /** Callbacks for the UI layer. Always invoked on the main thread. */
    public interface Listener {
        void onWorkersChanged();

        void onWorkerStatsUpdated();
    }

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final WorkerProcess[] slots = new WorkerProcess[MAX_WORKERS];
    private final ServiceConnection[] connections = new ServiceConnection[MAX_WORKERS];
    private final Messenger[] messengers = new Messenger[MAX_WORKERS];
    private final boolean[] stopping = new boolean[MAX_WORKERS];

    private final Messenger replyMessenger = new Messenger(new IncomingHandler());

    public DuplicateProcessManager(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    /** Starts one demo worker in the first free slot; {@code null} when all slots are busy. */
    public WorkerProcess addWorker(long allocBytes) {
        int slot = -1;
        for (int i = 0; i < MAX_WORKERS; i++) {
            if (slots[i] == null) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return null;
        }
        // Reserve the slot synchronously so rapid double-clicks cannot create duplicates.
        WorkerProcess worker = new WorkerProcess(slot, SLOT_LABELS[slot],
                SLOT_PROCESS_NAMES[slot], allocBytes);
        slots[slot] = worker;
        stopping[slot] = false;

        Intent intent = new Intent(context, SLOT_SERVICES[slot]);
        intent.putExtra(WorkerService.EXTRA_ALLOC_BYTES, allocBytes);
        ServiceConnection connection = new SlotConnection(slot);
        connections[slot] = connection;
        boolean bound;
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException exception) {
            bound = false;
        }
        if (!bound) {
            slots[slot] = null;
            connections[slot] = null;
            notifyWorkersChanged();
            return null;
        }
        notifyWorkersChanged();
        return worker;
    }

    /** Stops one worker and frees its slot. True when a worker was removed. */
    public boolean removeWorker(int slot) {
        if (slot < 0 || slot >= MAX_WORKERS || slots[slot] == null) {
            return false;
        }
        stopSlot(slot, WorkerProcess.Status.STOPPED);
        return true;
    }

    /**
     * Stops every running demo worker and clears the table. Returns the number of live
     * workers that were stopped (dead rows are only cleared). This is the single cleanup
     * path used by both the manual button and the 80% automatic cleanup.
     */
    public int removeAllWorkers() {
        int removed = 0;
        for (int i = 0; i < MAX_WORKERS; i++) {
            if (slots[i] == null) {
                continue;
            }
            if (slots[i].isAlive()) {
                removed++;
            }
            stopSlot(i, WorkerProcess.Status.STOPPED);
        }
        return removed;
    }

    /** Asks every live worker for a fresh PSS / private-dirty snapshot. */
    public void requestStats() {
        for (int i = 0; i < MAX_WORKERS; i++) {
            WorkerProcess worker = slots[i];
            Messenger messenger = messengers[i];
            if (worker == null || messenger == null || !worker.isAlive()) {
                continue;
            }
            Message request = Message.obtain(null, WorkerService.MSG_GET_STATUS);
            request.arg1 = i;
            request.replyTo = replyMessenger;
            try {
                messenger.send(request);
            } catch (RemoteException exception) {
                handleDied(i);
            }
        }
    }

    /** All current rows, in slot order (running first is not required). */
    public List<WorkerProcess> getWorkers() {
        List<WorkerProcess> workers = new ArrayList<>();
        for (WorkerProcess worker : slots) {
            if (worker != null) {
                workers.add(worker);
            }
        }
        return workers;
    }

    public int getAliveCount() {
        int count = 0;
        for (WorkerProcess worker : slots) {
            if (worker != null && worker.isAlive()) {
                count++;
            }
        }
        return count;
    }

    public int getRowCount() {
        int count = 0;
        for (WorkerProcess worker : slots) {
            if (worker != null) {
                count++;
            }
        }
        return count;
    }

    /** Sum of reported worker PSS in KiB (-1 values are skipped, not counted as zero). */
    public long getTotalPssKb() {
        long total = 0;
        boolean any = false;
        for (WorkerProcess worker : slots) {
            if (worker != null && worker.isAlive() && worker.getPssKb() >= 0) {
                total += worker.getPssKb();
                any = true;
            }
        }
        return any ? total : -1;
    }

    /** Unbinds and stops everything. Call from {@code onDestroy()}. */
    public void close() {
        for (int i = 0; i < MAX_WORKERS; i++) {
            if (slots[i] != null) {
                stopSlot(i, WorkerProcess.Status.STOPPED);
            }
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    // ----------------------------------------------------------------- internals

    private void stopSlot(int slot, WorkerProcess.Status finalStatus) {
        stopping[slot] = true;
        Messenger messenger = messengers[slot];
        if (messenger != null) {
            try {
                messenger.send(Message.obtain(null, WorkerService.MSG_STOP));
            } catch (RemoteException ignored) {
                // Already dead; unbinding below is enough.
            }
        }
        unbindQuietly(slot);
        try {
            context.stopService(new Intent(context, SLOT_SERVICES[slot]));
        } catch (RuntimeException ignored) {
            // Service may already be gone.
        }
        WorkerProcess worker = slots[slot];
        if (worker != null && worker.isAlive()) {
            worker.setStatus(finalStatus);
        }
        slots[slot] = null;
        messengers[slot] = null;
        connections[slot] = null;
        stopping[slot] = false;
        notifyWorkersChanged();
    }

    private void unbindQuietly(int slot) {
        ServiceConnection connection = connections[slot];
        if (connection == null) {
            return;
        }
        try {
            context.unbindService(connection);
        } catch (RuntimeException ignored) {
            // Not bound any more (e.g. the process already crashed).
        }
    }

    private void handleDied(int slot) {
        WorkerProcess worker = slots[slot];
        if (worker == null) {
            return;
        }
        if (!stopping[slot] && worker.isAlive()) {
            worker.setStatus(WorkerProcess.Status.KILLED);
        }
        unbindQuietly(slot);
        messengers[slot] = null;
        connections[slot] = null;
        notifyWorkersChanged();
    }

    private void notifyWorkersChanged() {
        if (listener != null) {
            listener.onWorkersChanged();
        }
    }

    private final class SlotConnection implements ServiceConnection {
        private final int slot;

        SlotConnection(int slot) {
            this.slot = slot;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (slots[slot] == null) {
                return;
            }
            messengers[slot] = new Messenger(service);
            slots[slot].setStatus(WorkerProcess.Status.RUNNING);
            notifyWorkersChanged();
            requestStats();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            handleDied(slot);
        }
    }

    /** Receives {@link WorkerService#MSG_STATUS} replies on the main thread. */
    private final class IncomingHandler extends Handler {
        IncomingHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what != WorkerService.MSG_STATUS) {
                super.handleMessage(msg);
                return;
            }
            int slot = msg.arg1;
            if (slot < 0 || slot >= MAX_WORKERS || slots[slot] == null) {
                return;
            }
            Bundle data = msg.getData();
            WorkerProcess worker = slots[slot];
            worker.setPid(data.getInt("pid", worker.getPid()));
            worker.setPssKb(data.getInt("pssKb", -1));
            worker.setPrivateDirtyKb(data.getInt("privateDirtyKb", -1));
            if (worker.isAlive()) {
                worker.setStatus(WorkerProcess.Status.RUNNING);
            }
            if (listener != null) {
                listener.onWorkerStatsUpdated();
            }
        }
    }
}
