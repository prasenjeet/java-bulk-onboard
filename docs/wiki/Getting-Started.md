# Getting Started

## Prerequisites

| Tool | Minimum version | Check |
|------|----------------|-------|
| Java JDK | 17 | `java -version` |
| Apache Maven | 3.8 | `mvn -version` |
| Modern browser | Any | Chrome / Firefox / Edge / Safari |

---

## 1 — Clone the Repository

```bash
git clone https://github.com/prasenjeet/java-bulk-onboard.git
cd java-bulk-onboard
```

---

## 2 — Start the Backend

```bash
cd backend
mvn spring-boot:run
```

You should see:

```
Started BulkOnboardApplication in 2.3 seconds (process running on port 8080)
```

The API is now available at **`http://localhost:8080`**.

> **Tip:** To change the port, edit `backend/src/main/resources/application.properties`
> and set `server.port=9090` (or any free port).

---

## 3 — Open the Frontend

**Option A — Open directly (no server needed)**

```
Open  frontend/index.html  in your browser
```

**Option B — Serve with Python**

```bash
cd frontend
python3 -m http.server 3000
# Visit http://localhost:3000
```

**Option C — Serve with Node.js `serve`**

```bash
npm install -g serve
serve frontend -p 3000
# Visit http://localhost:3000
```

---

## 4 — Upload Your First CSV

1. Click **⬇ Download Template** to get the blank CSV.
2. Fill it in (or use one of the files in `sample-data/`).
3. Drag it onto the upload zone (or click to browse).
4. Click **🚀 Start Onboarding**.
5. Watch the progress dashboard update in real time.

---

## Building a Production JAR

```bash
cd backend
mvn clean package -DskipTests
java -jar target/bulk-onboard-1.0.0.jar
```

The fat JAR also serves the frontend if you copy the `frontend/` folder
into `src/main/resources/static/` before building.

---

## Running Tests

```bash
cd backend
mvn test
```

---

## Directory Layout

```
java-bulk-onboard/
├── backend/                         ← Spring Boot application
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
├── frontend/                        ← AngularJS SPA
│   ├── index.html
│   ├── css/app.css
│   └── js/
│       ├── app.js
│       ├── onboardingService.js
│       └── onboardingController.js
├── sample-data/
│   ├── accounts_sample.csv
│   └── accounts_minimal.csv
└── docs/wiki/                       ← This wiki
```

---

## Troubleshooting

### "Upload failed. Is the server running on port 8080?"

The frontend cannot reach the backend. Verify:
- `mvn spring-boot:run` completed without errors.
- Nothing else is bound to port 8080 (`lsof -i :8080`).
- If you changed `server.port`, update `APP_CONFIG.apiBase` in `frontend/js/onboardingService.js`.

### "Invalid file type. Only .csv files are accepted."

The file must have a `.csv` extension. Rename it if necessary.

### Maven fails with "JAVA_HOME not set"

```bash
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
```
