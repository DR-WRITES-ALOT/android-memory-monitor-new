# Android Memory Monitor

A small Java and XML dashboard for a college demonstration of Android memory management
and OS scheduling concepts. It reports live system and app memory, manages real
**app-owned duplicate worker processes** to show memory distribution across processes,
performs **automatic cleanup at 80% RAM usage**, and includes an explicitly labelled
**educational CPU-scheduling simulator** (FCFS and Round Robin). Standard Android APIs and
`/proc/meminfo` are used; the dashboard refreshes every two seconds and keeps the latest
20 system RAM usage samples.

## Dashboard

Sections are laid out in this order:

1. **App header and current memory status** — title, a Normal / High-memory-pressure /
   Low-memory status chip and the last update time.
2. **System RAM** — `ActivityManager.getMemoryInfo()` provides total, available and
   low-memory status. Used RAM is total minus available, the percentage is derived from
   that estimate and a progress bar visualizes it. Values use GiB (1024³ bytes). Above
   **80%** a red warning banner appears and the progress bar turns red.
3. **App / kernel memory summary** — compact side-by-side cards. *This app*: public
   `Debug.getMemoryInfo()` and heap/runtime APIs provide PID, PSS, private dirty memory,
   native heap allocated and Java heap used (MiB, two decimals). *Kernel memory*: the app
   reads `/proc/meminfo` directly (MemTotal, MemFree, MemAvailable, Buffers, Cached,
   SwapTotal, SwapFree in MiB); a read or parse failure is shown safely.
4. **Duplicate demo processes** — create/remove real worker processes and see their
   memory (Feature A + B below).
5. **CPU scheduling simulator** — FCFS / Round Robin with a Gantt timeline (Feature C).
6. **RAM history** — a sparkline (with a dashed 80% threshold line) and the percentage
   sequence of the latest 20 system RAM samples, oldest to newest.
7. **Detailed process information** — `ActivityManager.getRunningAppProcesses()` and
   `getProcessMemoryInfo()` display names, PIDs and PSS only for processes Android
   exposes. Modern Android restricts visibility into unrelated apps. Results are not
   fabricated; the app uses neither root nor hidden APIs.

PSS (proportional set size) counts shared pages proportionally. Private dirty memory is
memory unique to the process that would require write-back if reclaimed. Java and native
heap measurements describe allocator usage and are not the same as PSS.

## Feature A — automatic duplicate-process cleanup at 80% RAM

- RAM usage is calculated from the same total/available measurements shown on the card.
- When usage reaches **80%**, a clear warning is shown and — if automatic cleanup is
  enabled — every running demo worker process of this app is stopped and its allocated
  memory released. **The main app and its monitoring keep running.**
- The dashboard then shows a cleanup report: how many workers were removed and the RAM
  usage **before and after** the cleanup (the "after" sample is taken on the next
  two-second refresh, when the worker processes are gone).
- Hysteresis prevents a cleanup storm: after one cycle the system must fall back to
  **75%** or below before another automatic cycle can trigger.
- If no demo workers exist when usage crosses 80%, the app says so honestly instead of
  pretending a cleanup happened.
- A switch enables/disables automatic cleanup; a **Cleanup now** button runs the same
  cleanup manually (with the same before/after report).

**Platform restriction:** the cleanup only ever stops the demo worker processes created
by this application. Android does not allow ordinary apps to uninstall or force-stop
unrelated third-party applications, and this app never attempts it.

## Feature B — duplicate processes and memory distribution

- **Add worker** starts a real Android Service that runs in its **own private process**
  (`android:process=":worker_a"` … `":worker_d"` in the manifest), so each worker has an
  independent PID and an independent memory footprint. A fixed maximum of **four**
  workers prevents excessive resource use; rapid double-clicks cannot exceed it.
- Each worker allocates a small configurable memory block (default **4 MiB**, editable
  1–32 MiB before adding, touched page by page so it is really resident) and releases it
  when the worker stops.
- The worker table shows: worker/process name, PID, PSS (MiB), private dirty memory
  (MiB, where available), each worker's share of the combined worker PSS (mini bar), and
  the current status (STARTING / RUNNING / STOPPED / KILLED).
- Workers report their own PSS via `Debug.getPss()`/`Debug.getMemoryInfo()` over a
  `Messenger`; unavailable values show as "unavailable" — never as fabricated zeros.
  If Android kills a worker, the row is marked **KILLED** and the app continues without
  crashing.
- The totals line shows the combined reported worker PSS and the allocated blocks. This
  total is the *sum of reported PSS values* with the usual PSS caveat (shared pages are
  counted proportionally), not additive independently-owned physical RAM on top of the
  app's own PSS.

### Demonstrating memory distribution

1. Add 2–4 workers and watch the table: separate PIDs (`:worker_a` …), each with its own
   PSS, and the per-worker share bar showing the relative distribution.
2. Compare the worker PIDs with the "Detailed process information" list — the workers of
   this app appear there as separate processes.
3. Raise the allocation size (e.g. 16 MiB) and add another worker: its PSS grows
   accordingly while the others stay flat.
4. Use **Remove all** (or the per-row ✕) and watch the worker rows clear and the
   processes disappear.

## Feature C — process scheduling demonstration

A dedicated **CPU scheduling simulator** section implements:

- **First Come, First Served (FCFS)** — non-preemptive, arrival order.
- **Round Robin (RR)** — configurable time quantum (1–10 ticks), ready queue served in
  order, arrivals during a quantum join the queue at the next scheduling point.

Demonstration behaviour:

- Each live demo worker becomes one scheduling job (P1, P2, …) labelled with its real
  worker name and PID. Arrival time and CPU burst are editable (ticks). With no workers,
  the section asks you to add some first.
- The job table shows job id, arrival time, CPU burst, **remaining burst** and the
  current **READY / RUNNING / COMPLETED** state as the simulated scheduler executes.
  (WAITING is not modelled: the demo jobs are pure CPU jobs — "READY" is exactly the
  state of waiting in the ready queue.)
- **Start / Pause / Resume / Reset** control an animated playback: the current job
  highlights in the table and a **Gantt chart** reveals each execution interval
  (`P1 0-4`, `P2 4-7`, `IDLE 8-10`, …).
- Per-job **waiting time** and **turnaround time** are shown after the run, plus the
  **averages**. Waiting time = turnaround time − burst time.
- Switch between FCFS and Round Robin and run both: the comparison line shows the two
  averages side by side so the class can see how the algorithm choice changes waiting
  and turnaround times.

**Important distinction:** this is an explicitly labelled *educational simulation* of the
scheduling concepts taught in OS courses. Ordinary Android apps cannot inspect the Linux
kernel's real scheduling decisions through public APIs, so the simulated states, ticks
and Gantt chart are **not** measurements of the real kernel scheduler. The scheduler
logic lives in `SchedulerSimulator.java` (pure Java, unit-tested) and only borrows the
worker identities for the job labels.

## Refresh and lifecycle

`MainActivity` uses one main-thread `Handler`/`Runnable` loop. It refreshes immediately
and schedules the next update after 2,000 milliseconds. Memory collection
(`/proc/meminfo`, `getProcessMemoryInfo`, …) runs on a background executor so the main
thread never blocks; UI updates happen on the main thread, and stale async results are
dropped. `onDestroy()` removes pending callbacks, shuts down the executor, unbinds the
worker services and releases the scheduler playback. The history is held in memory and
starts fresh when the Activity is recreated.

## Project files

- `MainActivity.java` — UI, refresh loop, cleanup flow, scheduling controls.
- `MemoryMonitor.java` — system RAM, `/proc/meminfo`, own-process and visible-process
  readings, 20-sample history.
- `ProcessInfo.java` — memory model/formatting for this app's process.
- `WorkerProcess.java` — model for one demo worker (PID, PSS, status, …).
- `WorkerService.java` + `WorkerAService`…`WorkerDService` — demo workers, one Android
  Service per private process, allocation + `Messenger` status reporting.
- `DuplicateProcessManager.java` — worker lifecycle, stats collection, cleanup.
- `MemoryThresholdManager.java` — pure-Java 80%/75% threshold state machine.
- `SchedulerSimulator.java`, `SchedulingJob.java`, `ScheduleResult.java` — pure-Java
  FCFS/RR simulation with Gantt slices and timing results.
- `SchedulerController.java` — playback (Start/Pause/Resume/Reset) for the simulator.
- `SparklineView.java`, `GanttChartView.java` — compact custom views.
- `activity_main.xml`, `item_worker.xml`, `item_job.xml` — the redesigned dashboard.
- Unit tests: `MemoryThresholdManagerTest.java`, `SchedulerSimulatorTest.java`.

## Build

The project retains its configured Gradle 8.9, Android Gradle Plugin 8.7.3, JDK 17,
minSdk 24, compileSdk 35 setup. Build the debug app:

```bash
./gradlew assembleDebug        # Linux/macOS
.\gradlew.bat assembleDebug    # Windows PowerShell
```

The APK is generated under `app/build/outputs/apk/debug/`. Run the unit tests with
`./gradlew test` (threshold hysteresis, FCFS, Round Robin, waiting/turnaround times).

## Demonstrating the whole story (suggested script)

1. Launch the app — the System RAM card, kernel card and history fill in live.
2. Add two workers → separate PIDs and PSS values appear in the table.
3. Start the scheduler with FCFS → watch the Gantt chart; note the averages.
4. Switch to Round Robin (quantum 2) → run again; compare waiting/turnaround on the
   comparison line.
5. On an emulator with little RAM (or after filling RAM), watch usage cross 80%: the
   warning banner appears and the workers are cleaned automatically with a before/after
   report. On a device with plenty of free RAM, press **Cleanup now** to demonstrate the
   same mechanism on demand.
6. Lower memory pressure (close apps / wait) and confirm the cycle re-arms at 75%.

## Android platform limitations

- **80% may not trigger on modern devices.** The threshold is measured against total
  device RAM. Phones with 8+ GB rarely hit 80%; demonstrate on a low-RAM emulator or
  after filling memory, or use the manual **Cleanup now** button.
- **PSS is not additive.** Summing PSS values across processes double-counts nothing but
  also does not equal uniquely owned RAM: shared pages are counted proportionally per
  process.
- **Process visibility is limited.** Since Android 7+, `getRunningAppProcesses()` and
  `getProcessMemoryInfo()` only expose the caller's own processes reliably. The app
  shows what the public API returns and labels the rest "unavailable".
- **The scheduler is simulated.** No public Android API exposes kernel scheduling
  decisions; the Gantt chart and tick timings are educational, not measurements.
- **No root, no hidden APIs.** `/proc/meminfo` is read-only; worker cleanup cannot (and
  does not) uninstall or force-stop other apps — that is restricted by Android.
- Workers live inside the app's process group: if the app process itself is killed, the
  workers go with it. The in-memory history and scheduler state reset on recreation.
