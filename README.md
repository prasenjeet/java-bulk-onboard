# Bulk Account Onboarding

A full-stack application for onboarding multiple accounts via CSV upload.
- **Backend** – Spring Boot 3 REST API with async processing & Server-Sent Events
- **Frontend** – AngularJS 1.8 SPA with live progress dashboard

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                       AngularJS Frontend                         │
│  ┌─────────────────┐   ┌──────────────────────────────────────┐  │
│  │  Upload Card     │   │         Progress Dashboard           │  │
│  │  ─────────────  │   │  KPI tiles │ Progress bar │ Failures │  │
│  │  Drag & Drop    │   │  SSE stream (real-time updates)      │  │
│  └────────┬────────┘   └──────────────────────────────────────┘  │
└───────────┼──────────────────────────────────────────────────────┘
            │ HTTP POST multipart/form-data
            ▼
┌──────────────────────────────────────────────────────────────────┐
│                     Spring Boot REST API (:8080)                 │
│                                                                  │
│  POST  /api/onboard/upload          Upload CSV → returns jobId  │
│  GET   /api/onboard/jobs/{jobId}    Poll status (JSON)          │
│  GET   /api/onboard/jobs/{jobId}/sse  SSE stream (live updates) │
│  GET   /api/onboard/jobs            List all jobs               │
│  GET   /api/onboard/template        Download blank CSV template │
│                                                                  │
│  OnboardingService (async)                                       │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  CSV Parse → Validate → Simulate Processing → JobStore  │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17 +    |
| Maven | 3.8 +  |
| Browser | Any modern browser |

---

## Quick Start

### 1 — Start the Backend

```bash
cd backend
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

### 2 — Open the Frontend

Open `frontend/index.html` directly in your browser **or** serve it with any
static file server:

```bash
# Python 3 (from repo root)
cd frontend
python3 -m http.server 3000
# → http://localhost:3000
```

> The frontend talks to `http://localhost:8080` by default.
> To change the API URL, edit `frontend/js/onboardingService.js` → `APP_CONFIG.apiBase`.

---

## CSV Format

Download the template from the UI or call `GET /api/onboard/template`.

| Column | Required | Notes |
|--------|----------|-------|
| `account_id`   | ✅ | Unique identifier |
| `account_name` | ✅ | Full name / company name |
| `email`        | ✅ | Must be valid email format |
| `phone`        | ✅ | Any format |
| `account_type` | ✅ | `PERSONAL`, `BUSINESS`, `CORPORATE`, `SAVINGS`, `CHECKING` |
| `country`      | ✅ | ISO 2-letter code |
| `currency`     | ✅ | `USD`, `EUR`, `GBP`, `JPY`, `CAD`, `AUD`, `INR`, `SGD`, `AED`, `CHF` |
| `credit_limit` | ❌ | Numeric, must be ≥ 0 |
| `status`       | ❌ | Free text |

Sample files are in `sample-data/`:
- `accounts_sample.csv` — 20 records, includes intentional failures for demo
- `accounts_minimal.csv` — 3 clean records

---

## API Reference

### `POST /api/onboard/upload`

Upload a CSV file for processing.

**Request:** `multipart/form-data` with field `file`

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "CSV uploaded successfully. Processing 20 record(s).",
  "totalRecords": 20
}
```

---

### `GET /api/onboard/jobs/{jobId}`

Poll the status of a single job.

**Response:**
```json
{
  "jobId": "550e8400-...",
  "fileName": "accounts_sample.csv",
  "state": "PROCESSING",
  "totalRecords": 20,
  "processedCount": 8,
  "successCount": 6,
  "failureCount": 2,
  "percentageComplete": 40.0,
  "percentageRemaining": 60.0,
  "startedAt": "2024-01-15T10:30:00Z",
  "completedAt": "",
  "errorMessage": "",
  "failures": [
    {
      "accountId": "ACC011",
      "accountName": "BadRecord One",
      "email": "not-an-email",
      "success": false,
      "failureReason": "email format is invalid: not-an-email"
    }
  ]
}
```

---

### `GET /api/onboard/jobs/{jobId}/sse`

Server-Sent Events stream. The client receives `progress` events at ~500 ms intervals.

```javascript
const es = new EventSource('/api/onboard/jobs/{jobId}/sse');
es.addEventListener('progress', e => {
  const data = JSON.parse(e.data);   // same shape as JSON poll response
});
```

---

### `GET /api/onboard/jobs`

Returns an array of all jobs (most recent first).

---

### `GET /api/onboard/template`

Downloads `onboard_template.csv` with two example rows.

---

## UI Features

| Feature | Description |
|---------|-------------|
| **Drag & Drop upload** | Drop a CSV onto the upload zone or click to browse |
| **Template download** | One-click download of the blank CSV template |
| **Real-time KPI tiles** | Total / Processed / Successful / Failed record counts |
| **Animated progress bar** | Live fill with shimmer animation while processing |
| **Percentage display** | % complete and % remaining updated every 500 ms |
| **Success-rate bar** | Secondary bar showing success vs failure ratio |
| **Failures table** | Lists every failed account with its failure reason |
| **Export failures** | Download failed records as a new CSV for correction |
| **Job history** | Table of all past jobs with links to review any job |

---

## Project Structure

```
java-bulk-onboard/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/bulkonboard/
│       │   ├── BulkOnboardApplication.java
│       │   ├── config/CorsConfig.java
│       │   ├── controller/OnboardingController.java
│       │   ├── model/
│       │   │   ├── AccountRecord.java
│       │   │   ├── JobStatus.java
│       │   │   ├── ProcessingResult.java
│       │   │   └── UploadResponse.java
│       │   └── service/OnboardingService.java
│       └── resources/application.properties
├── frontend/
│   ├── index.html
│   ├── css/app.css
│   └── js/
│       ├── app.js
│       ├── onboardingService.js
│       └── onboardingController.js
├── sample-data/
│   ├── accounts_sample.csv   ← 20 records (includes intentional bad data)
│   └── accounts_minimal.csv  ← 3 clean records
└── README.md
```

---

## Configuration

Edit `backend/src/main/resources/application.properties`:

```properties
# Simulated processing delay per record (ms)  – set to 0 for instant
app.onboarding.processing-delay-ms=300

# Max upload size
spring.servlet.multipart.max-file-size=10MB
```

---

## Validation Rules

Each row is validated before processing:

| Rule | Error |
|------|-------|
| Missing `account_id` | `account_id is required` |
| Missing / invalid email | `email format is invalid` |
| Unknown `account_type` | `account_type must be one of [...]` |
| Unknown `currency` | `currency must be one of [...]` |
| Negative `credit_limit` | `credit_limit cannot be negative` |
| Non-numeric `credit_limit` | `credit_limit must be numeric` |

In addition, ~10% of otherwise-valid records experience a simulated
downstream timeout to demonstrate failure handling in a real system.
