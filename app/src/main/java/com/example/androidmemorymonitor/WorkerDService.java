package com.example.androidmemorymonitor;

/** Demo worker D, running in its own process {@code :worker_d} (see the manifest). */
public class WorkerDService extends WorkerService {
    @Override
    protected String getWorkerName() {
        return "Worker D";
    }
}
