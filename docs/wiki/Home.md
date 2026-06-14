# Bulk Account Onboarding — Wiki

Welcome to the **Bulk Account Onboarding** project wiki.  
This application lets you onboard hundreds of accounts in a single CSV upload while watching real-time progress in a live dashboard.

---

## Quick Navigation

| Page | Description |
|------|-------------|
| [Getting Started](Getting-Started) | Install, build, and run in under 5 minutes |
| [CSV Format](CSV-Format) | Template columns, validation rules, and sample data |
| [API Reference](API-Reference) | Every REST endpoint with request/response examples |
| [Architecture](Architecture) | System design, data flow, and component diagram |
| [Frontend Guide](Frontend-Guide) | AngularJS UI walkthrough and customisation |
| [Configuration](Configuration) | Tuning the backend for production use |

---

## What It Does

```
User uploads CSV  →  Spring Boot API  →  Async processing
                                              │
                          ┌───────────────────┴───────────────────┐
                          │           SSE live stream              │
                          └──────────────────────────────────────▶ │
                                                    AngularJS Dashboard
                                                    • KPI tiles
                                                    • Animated progress bar
                                                    • % complete / % remaining
                                                    • Failures table with reasons
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | AngularJS 1.8 |
| Backend | Spring Boot 3.2 · Java 17 |
| CSV parsing | OpenCSV 5.9 |
| Real-time | Server-Sent Events (SSE) |
| Build | Maven 3.8+ |

---

## Sample Files

Two ready-to-upload CSV files are included in `sample-data/`:

| File | Records | Notes |
|------|---------|-------|
| `accounts_sample.csv` | 20 | 4 intentional bad rows — great for demoing failures |
| `accounts_minimal.csv` | 3 | Clean rows for a quick smoke-test |
