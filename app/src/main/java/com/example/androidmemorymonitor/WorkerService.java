package com.example.androidmemorymonitor;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;

/**
 * Base class for the demo worker services.
 *
 * <p>Four thin subclasses ({@link WorkerAService}..{@link WorkerDService}) each run in
 * their own private Android process so a college demo can show real multi-process memory
 * distribution inside one application. Every worker allocates a small configurable memory
 * block (touched page by page so it is really resident) and reports its PID, PSS and
 * private dirty memory to the UI over a {@link Messenger}.</p>
 *
 * <p>Cleanup only ever targets these app-owned workers: the service releases its block in
 * {@link #onDestroy()} and a stop request also terminates its own process. The app never
 * uninstalls or force-stops anything else — Android does not allow ordinary apps to do
 * that.</p>
 */
public abstract class WorkerService extends Service {

    /** Client asks the worker for a status reply. arg1 is echoed back to the client. */
    public static final int MSG_GET_STATUS = 1;
    /** Worker's reply: pid, PSS, private dirty, allocated bytes, name. */
    public static final int MSG_STATUS = 2;
    /** Client tells the worker to release memory and stop its process. */
    public static final int MSG_STOP = 3;

    public static final String EXTRA_ALLOC_BYTES = "alloc_bytes";

    /** Conservative default memory block: 4 MiB. */
    public static final long DEFAULT_ALLOC_BYTES = 4L * 1024L * 1024L;
    /** Hard cap so a misconfigured request cannot exhaust the device. */
    public static final long MAX_ALLOC_BYTES = 32L * 1024L * 1024L;

    private byte[] memoryBlock;
    private long allocatedBytes;
    private Messenger messenger;

    /** Human readable worker name, e.g. "Worker A". */
    protected abstract String getWorkerName();

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_GET_STATUS:
                    replyStatus(msg);
                    break;
                case MSG_STOP:
                    releaseMemory();
                    stopSelf();
                    // This process hosts only this service, so ending it immediately
                    // frees its memory while the main app keeps monitoring.
                    Process.killProcess(Process.myPid());
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        messenger = new Messenger(handler);
    }

    @Override
    public IBinder onBind(Intent intent) {
        allocateMemory(intent);
        return messenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        allocateMemory(intent);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseMemory();
        super.onDestroy();
    }

    private void allocateMemory(Intent intent) {
        if (memoryBlock != null) {
            return;
        }
        long requested = DEFAULT_ALLOC_BYTES;
        if (intent != null && intent.hasExtra(EXTRA_ALLOC_BYTES)) {
            requested = intent.getLongExtra(EXTRA_ALLOC_BYTES, DEFAULT_ALLOC_BYTES);
        }
        long bounded = Math.max(0, Math.min(requested, MAX_ALLOC_BYTES));
        int size = (int) bounded;
        memoryBlock = new byte[size];
        // Touch every page so the allocation is resident and visible in PSS.
        for (int offset = 0; offset < size; offset += 4096) {
            memoryBlock[offset] = 1;
        }
        allocatedBytes = bounded;
    }

    private void releaseMemory() {
        memoryBlock = null;
        allocatedBytes = 0;
    }

    private void replyStatus(Message request) {
        Bundle data = new Bundle();
        data.putInt("pid", Process.myPid());
        data.putString("workerName", getWorkerName());
        data.putLong("allocatedBytes", allocatedBytes);
        data.putInt("pssKb", safePssKb());
        data.putInt("privateDirtyKb", safePrivateDirtyKb());
        Message reply = Message.obtain(null, MSG_STATUS);
        reply.arg1 = request.arg1;
        reply.setData(data);
        try {
            if (request.replyTo != null) {
                request.replyTo.send(reply);
            }
        } catch (RemoteException ignored) {
            // The UI process went away; nothing to clean up here.
        }
    }

    private int safePssKb() {
        try {
            return (int) Math.min(Integer.MAX_VALUE, Debug.getPss());
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private int safePrivateDirtyKb() {
        try {
            Debug.MemoryInfo info = new Debug.MemoryInfo();
            Debug.getMemoryInfo(info);
            return info.getTotalPrivateDirty();
        } catch (RuntimeException exception) {
            return -1;
        }
    }
}
