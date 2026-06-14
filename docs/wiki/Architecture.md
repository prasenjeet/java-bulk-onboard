# Architecture

---

## High-Level Flow

```
┌──────────────────────────────────────────────────────────────┐
│                     Browser (AngularJS)                      │
│                                                              │
│  ┌──────────────┐   POST /upload    ┌────────────────────┐  │
│  │  Upload Card │ ────────────────▶ │  OnboardingCtrl    │  │
│  │  (CSV file)  │ ◀──── jobId ───── │  onboardingService │  │
│  └──────────────┘                   └────────┬───────────┘  │
│                                              │ SSE stream    │
│  ┌───────────────────────────────────────────▼────────────┐  │
│  │              Progress Dashboard                        │  │
│  │  KPIs │ Progress Bar │ % complete │ Failures Table    │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTP (CORS enabled)
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                 Spring Boot API (:8080)                      │
│                                                              │
│  OnboardingController                                        │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  POST /upload  →  parse CSV  →  submit job          │    │
│  │  GET  /jobs/{id}      →  JSON snapshot              │    │
│  │  GET  /jobs/{id}/sse  →  SSE push loop              │    │
│  │  GET  /jobs           →  list all jobs              │    │
│  │  GET  /template       →  download blank CSV         │    │
│  └──────────────────────────┬──────────────────────────┘    │
│                             │                               │
│  OnboardingService (@Async) │                               │
│  ┌──────────────────────────▼──────────────────────────┐    │
│  │  Parse CSV (OpenCSV)                                │    │
│  │  ──▶  For each AccountRecord:                       │    │
│  │         validate()  →  errors list                  │    │
│  │         Thread.sleep(processingDelayMs)             │    │
│  │         10% random downstream failure               │    │
│  │         job.recordSuccess() OR job.recordFailure()  │    │
│  └──────────────────────────┬──────────────────────────┘    │
│                             │                               │
│  JobStore (ConcurrentHashMap<String, JobStatus>)            │
│  ┌──────────────────────────▼──────────────────────────┐    │
│  │  jobId → JobStatus (AtomicInteger counters)         │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

---

## Component Breakdown

### Backend

#### `BulkOnboardApplication`
Spring Boot entry point.  `@EnableAsync` activates the async thread pool
so that `OnboardingService.processAsync()` runs off the HTTP thread.

#### `OnboardingController`
- Maps REST endpoints.
- Calls `OnboardingService.submitJob()` and returns a `UploadResponse` immediately.
- The `/sse` endpoint spins up a task in `ssePool` (cached thread pool) that
  sleeps 500 ms between `emitter.send()` calls.

#### `OnboardingService`
- Parses the `MultipartFile` into a `List<AccountRecord>` via OpenCSV's
  `CsvToBeanBuilder`.
- Stores a new `JobStatus` in `jobStore` and kicks off `processAsync()`.
- `processAsync()` is `@Async` — it runs on Spring's `SimpleAsyncTaskExecutor`
  (one thread per call; replace with a bounded pool for production).
- Each record is validated, then a `Thread.sleep` simulates I/O latency,
  then `~10 %` random failure mimics a real downstream system.

#### `JobStatus`
Thread-safe state holder:
- `AtomicInteger` for `processedCount`, `successCount`, `failureCount`.
- `volatile` for `state`, `errorMessage`, `totalRecords`.
- `synchronized` on `recordFailure()` to protect the `failures` `ArrayList`.

#### `AccountRecord`
OpenCSV bean.  `@CsvBindByName(column = "...")` annotations map CSV header
names to Java fields.

#### `CorsConfig`
Adds `Access-Control-Allow-Origin: *` to all `/api/**` paths.

---

### Frontend

#### `app.js`
Declares the `bulkOnboardApp` Angular module.

#### `onboardingService.js`
Thin wrapper around `$http`:
- `uploadCsv(file)` — sends `multipart/form-data`.
- `streamJobStatus(jobId, onProgress, onComplete, onError)` — opens an
  `EventSource` and calls callbacks; falls back to 1-second polling if
  `window.EventSource` is absent.
- `listJobs()`, `getJobStatus(jobId)`, `downloadTemplate()`.

#### `onboardingController.js`
Drives all UI state.  Key responsibilities:
- Drag-and-drop file handling via native DOM events forwarded into `$scope.$apply`.
- Opens the SSE stream after upload, closes it when the job finishes or the
  controller is destroyed (`$scope.$on('$destroy')`).
- `exportFailures()` uses the Blob/URL API to generate an in-browser CSV download.

---

## Sequence Diagram — Upload & Track

```
Browser          Controller       Service          JobStore
   │                  │               │                │
   │── POST /upload ─▶│               │                │
   │                  │── submitJob ─▶│                │
   │                  │              │── parseCsv      │
   │                  │              │── new JobStatus─▶│
   │                  │              │── processAsync()│
   │                  │◀── jobId ────│               │
   │◀── 200 {jobId} ──│               │                │
   │                  │            (async thread)      │
   │── GET /sse ──────▶│              │── validate()   │
   │◀── event:progress│              │── recordSuccess│
   │◀── event:progress│              │── recordFailure│
   │       …          │               │                │
   │◀── event:progress│         state=COMPLETED        │
   │  (stream closed) │               │                │
```

---

## Concurrency Model

```
HTTP thread       ─── returns immediately after submitJob()
Async thread      ─── processes records sequentially, updates AtomicIntegers
SSE thread pool   ─── reads volatile/atomic fields every 500 ms, pushes events
```

Records within a single job are processed **sequentially** (one at a time).
Multiple *jobs* are processed concurrently because each is on its own async thread.

---

## Extension Points

| What to change | Where |
|----------------|-------|
| Replace in-memory store with DB | `OnboardingService.jobStore` |
| Use a bounded thread pool | `@Configuration` + `TaskExecutor` bean |
| Add authentication | Spring Security `SecurityFilterChain` |
| Add record-level retry | `OnboardingService.processAsync()` |
| Persist failures to DB | `OnboardingService.recordFailure()` |
| Lock CORS to specific domain | `CorsConfig.addCorsMappings()` |
| Bundle frontend into the JAR | Copy `frontend/` → `src/main/resources/static/` |
