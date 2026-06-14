# API Reference

Base URL: `http://localhost:8080/api/onboard`

All endpoints return JSON unless stated otherwise.  CORS is open to all
origins in development; lock it down in `CorsConfig.java` before deploying.

---

## Endpoints at a Glance

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/upload` | Upload a CSV and start processing |
| `GET` | `/jobs/{jobId}` | Poll a single job status |
| `GET` | `/jobs/{jobId}/sse` | Server-Sent Events live stream |
| `GET` | `/jobs` | List all jobs |
| `GET` | `/template` | Download blank CSV template |

---

## POST `/upload`

Upload a CSV file and receive a `jobId` to track progress.

### Request

`Content-Type: multipart/form-data`

| Field | Type | Description |
|-------|------|-------------|
| `file` | File | The `.csv` file to upload |

#### cURL Example

```bash
curl -X POST http://localhost:8080/api/onboard/upload \
  -F "file=@sample-data/accounts_sample.csv"
```

### Response `200 OK`

```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "CSV uploaded successfully. Processing 20 record(s).",
  "totalRecords": 20
}
```

### Error Responses

| Status | Body | Cause |
|--------|------|-------|
| `400` | `{ "error": "No file selected…" }` | Empty multipart field |
| `400` | `{ "error": "Invalid file type…" }` | File is not `.csv` |
| `500` | `{ "error": "Failed to process CSV: …" }` | CSV parse failure |

---

## GET `/jobs/{jobId}`

Returns a JSON snapshot of the job at the current moment.  Poll this every
1–2 seconds when Server-Sent Events are not available.

### Request

```bash
curl http://localhost:8080/api/onboard/jobs/550e8400-e29b-41d4-a716-446655440000
```

### Response `200 OK`

```json
{
  "jobId":               "550e8400-e29b-41d4-a716-446655440000",
  "fileName":            "accounts_sample.csv",
  "state":               "PROCESSING",
  "totalRecords":        20,
  "processedCount":      8,
  "successCount":        6,
  "failureCount":        2,
  "percentageComplete":  40.0,
  "percentageRemaining": 60.0,
  "startedAt":           "2024-01-15T10:30:00Z",
  "completedAt":         "",
  "errorMessage":        "",
  "failures": [
    {
      "accountId":     "ACC011",
      "accountName":   "BadRecord One",
      "email":         "not-an-email",
      "success":       false,
      "failureReason": "email format is invalid: not-an-email",
      "processingTimeMs": 312
    }
  ]
}
```

### State Values

| `state` | Meaning |
|---------|---------|
| `QUEUED` | Job received, not yet started |
| `PROCESSING` | Records are being processed |
| `COMPLETED` | All records processed (some may have failed) |
| `FAILED` | Job-level error (not per-record failures) |

### Error Response

| Status | Cause |
|--------|-------|
| `404` | `jobId` is unknown |

---

## GET `/jobs/{jobId}/sse`

Server-Sent Events stream.  The server pushes a `progress` event approximately
every **500 ms** until the job reaches `COMPLETED` or `FAILED`, then closes
the stream.

### Request

```javascript
const es = new EventSource(
  'http://localhost:8080/api/onboard/jobs/550e8400-.../sse'
);

es.addEventListener('progress', event => {
  const data = JSON.parse(event.data);
  console.log(data.percentageComplete + '% done');

  if (data.state === 'COMPLETED' || data.state === 'FAILED') {
    es.close();
  }
});

es.addEventListener('error', event => {
  if (es.readyState === EventSource.CLOSED) {
    console.log('Stream closed normally');
  }
});
```

### Event Shape

Each `progress` event data is the same JSON object as the `/jobs/{jobId}` poll
response (see above).

### SSE Fallback

The AngularJS frontend automatically falls back to 1-second polling if
`window.EventSource` is not available.

---

## GET `/jobs`

Returns an array of all jobs, most-recent first.

### Request

```bash
curl http://localhost:8080/api/onboard/jobs
```

### Response `200 OK`

```json
[
  {
    "jobId": "550e8400-...",
    "fileName": "accounts_sample.csv",
    "state": "COMPLETED",
    "totalRecords": 20,
    "processedCount": 20,
    "successCount": 15,
    "failureCount": 5,
    "percentageComplete": 100.0,
    "percentageRemaining": 0.0,
    "startedAt": "2024-01-15T10:30:00Z",
    "completedAt": "2024-01-15T10:30:11Z",
    "errorMessage": "",
    "failures": [ /* ... */ ]
  }
]
```

> **Note:** Job history is stored in memory.  It is lost when the
> server restarts.  Replace `OnboardingService.jobStore` with a database
> or Redis for persistence.

---

## GET `/template`

Downloads a blank `onboard_template.csv` with two illustrative rows.

### Request

```bash
curl -O http://localhost:8080/api/onboard/template
```

### Response

`Content-Type: text/csv`  
`Content-Disposition: attachment; filename="onboard_template.csv"`

```csv
account_id,account_name,email,phone,account_type,country,currency,credit_limit,status
ACC001,John Doe,john.doe@example.com,+1-555-0100,PERSONAL,US,USD,5000.00,ACTIVE
ACC002,Acme Corp,billing@acme.com,+1-555-0200,BUSINESS,US,USD,50000.00,ACTIVE
```

---

## Data Types Reference

| Field | Java type | JSON type |
|-------|-----------|-----------|
| `jobId` | `String` (UUID) | string |
| `state` | `JobStatus.State` (enum) | string |
| `totalRecords` | `int` | number |
| `processedCount` | `AtomicInteger` | number |
| `successCount` | `AtomicInteger` | number |
| `failureCount` | `AtomicInteger` | number |
| `percentageComplete` | `double` (1 dp) | number |
| `percentageRemaining` | `double` (1 dp) | number |
| `startedAt` | `Instant` | ISO-8601 string |
| `completedAt` | `Instant` | ISO-8601 string or `""` |
| `errorMessage` | `String` | string or `""` |
| `failures` | `List<ProcessingResult>` | array |
