package com.example.androidmemorymonitor;

/** Demo worker C, running in its own process {@code :worker_c} (see the manifest). */
public class WorkerCService extends WorkerService {
    @Override
    protected String getWorkerName() {
        return "Worker C";
    }
}
