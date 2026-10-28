# Android Memory Monitor

A small Java and XML dashboard for a college demonstration of Android memory reporting. It uses standard Android APIs and `/proc/meminfo`, refreshes every two seconds, and keeps the latest 20 system RAM usage samples.

## Dashboard

- **System RAM:** `ActivityManager.getMemoryInfo()` provides total, available and low-memory status. Used RAM is calculated as total minus available, the percentage is derived from that estimate, and a horizontal progress bar visualizes the percentage. Values use GiB (1024³ bytes).
- **Kernel memory:** the app reads `/proc/meminfo` directly and displays MemTotal, MemFree, MemAvailable, Buffers, Cached, SwapTotal and SwapFree in MiB. A read or parse failure is shown safely in the dashboard.
- **This app:** public `Debug.getMemoryInfo()` and heap/runtime APIs provide PID, PSS, private dirty memory, native heap allocated and Java heap used. Values use MiB and two decimal places. PSS and private dirty memory are process measurements, not heap measurements.
- **Visible processes:** `ActivityManager.getRunningAppProcesses()` and `getProcessMemoryInfo()` display names, PIDs and PSS only for processes Android exposes. Modern Android restricts visibility into unrelated apps. Results are not fabricated; the app uses neither root nor hidden APIs.
- **History:** a compact sparkline and percentage sequence show up to the latest 20 system RAM usage samples, oldest to newest.

PSS (proportional set size) counts shared pages proportionally. Private dirty memory is memory unique to the process that would require write-back if reclaimed. Java and native heap measurements describe allocator usage and are not the same as PSS.

## Refresh and lifecycle

`MainActivity` uses one main-thread `Handler`/`Runnable` loop. It refreshes immediately and schedules the next update after 2,000 milliseconds. `onDestroy()` removes the pending callback. The history is held in memory and starts fresh when the Activity/process is recreated.

## Project files

- `MainActivity.java` binds the views, formats system RAM and visible process data, and owns the refresh callback.
- `MemoryMonitor.java` reads system RAM and `/proc/meminfo`, obtains this app's memory snapshot, and retains history.
- `ProcessInfo.java` stores and formats this app's memory values.
- `activity_main.xml` defines the scrollable Android Views dashboard.
- `README.md` and `VIVA_NOTES.md` document the actual implementation.

## Build

The project retains its configured Gradle 8.9, Android Gradle Plugin 8.7.3, JDK 17, minSdk 24 and compileSdk 35 setup. Build the debug app from PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The APK is generated under `app/build/outputs/apk/debug/`.
