package com.example.androidmemorymonitor;

/** Demo worker A, running in its own process {@code :worker_a} (see the manifest). */
public class WorkerAService extends WorkerService {
    @Override
    protected String getWorkerName() {
        return "Worker A";
    }
}
