package com.example.androidmemorymonitor;

/** Demo worker B, running in its own process {@code :worker_b} (see the manifest). */
public class WorkerBService extends WorkerService {
    @Override
    protected String getWorkerName() {
        return "Worker B";
    }
}
