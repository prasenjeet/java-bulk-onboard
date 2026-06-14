# Frontend Guide

The frontend is a single-page application built with **AngularJS 1.8**.
No build step is required — open `frontend/index.html` in any browser.

---

## File Structure

```
frontend/
├── index.html               ← Shell page; all ng-* directives live here
├── css/
│   └── app.css              ← All styles (variables, layout, components)
└── js/
    ├── app.js               ← Module declaration
    ├── onboardingService.js ← HTTP / SSE layer
    └── onboardingController.js ← All UI logic
```

---

## Sections of the UI

### Header
Sticky top bar with the app logo and anchor nav links.

### Upload Card
1. **Template download button** — calls `GET /api/onboard/template`.
2. **Drop zone** — accepts drag-and-drop or click-to-browse.  Shows file name
   and size once a file is selected.  Click the ✕ to clear.
3. **🚀 Start Onboarding** button — disabled until a `.csv` file is chosen.

### Progress Dashboard *(appears after upload)*

| Element | Detail |
|---------|--------|
| State badge | `QUEUED` / `PROCESSING` (pulsing blue) / `COMPLETED` (green) / `FAILED` (red) |
| KPI tiles | Total · Processed · ✅ Successful · ❌ Failed |
| Progress bar | Animated shimmer while processing; fills to 100% on completion |
| % display | "40.0% complete · 60.0% remaining" updated every 500 ms |
| Success-rate bar | Thin secondary bar showing success vs failure ratio |
| Job metadata | File name, Job ID, start time |

### Failures Table *(appears when ≥ 1 failure)*

Shows every failed account with:
- Row number
- Account ID
- Account name
- Email
- Failure reason (styled as a red tag)

**Export Failures** button generates an in-browser CSV download of the failures.

### Job History

A table of all jobs processed since the server started.  Click **View** to
load any past job into the progress dashboard.

---

## AngularJS Architecture

### Module (`app.js`)

```javascript
angular.module('bulkOnboardApp', []);
```

No third-party Angular modules are used — only core AngularJS 1.8.

### Service (`onboardingService.js`)

Registered as a `.service()` so it is a singleton across the app.

| Method | Description |
|--------|-------------|
| `uploadCsv(file)` | `POST /upload` with `FormData` |
| `streamJobStatus(jobId, onProgress, onComplete, onError)` | Opens `EventSource`; calls callbacks |
| `getJobStatus(jobId)` | `GET /jobs/{jobId}` |
| `listJobs()` | `GET /jobs` |
| `downloadTemplate()` | Opens `/template` in a new tab |

**SSE fallback logic:**
```javascript
if (!window.EventSource) {
  // 1-second polling loop
}
```

### Controller (`onboardingController.js`)

Uses the **controller-as** syntax (`ng-controller="OnboardingController as vm"`).

Key state variables on `vm`:

| Variable | Type | Purpose |
|----------|------|---------|
| `vm.selectedFile` | `File` | The chosen CSV file |
| `vm.uploading` | `boolean` | Disables the upload button |
| `vm.uploadError` | `string` | Shown in the alert box |
| `vm.activeJob` | `object` | Live job snapshot from SSE |
| `vm.jobHistory` | `array` | List from `GET /jobs` |
| `vm.isDragging` | `boolean` | Adds `drag-over` CSS class |

---

## Drag-and-Drop Implementation

AngularJS doesn't ship native drag-and-drop directives, so native DOM event
handlers forward into the digest cycle via `$scope.$apply`:

```html
ondragover="angular.element(this).scope().vm.onDragOver($event)"
ondragleave="angular.element(this).scope().vm.onDragLeave($event)"
ondrop="angular.element(this).scope().vm.onDrop($event)"
```

```javascript
vm.onDrop = function($event) {
  $event.preventDefault();
  var file = $event.dataTransfer.files[0];
  $scope.$apply(function() {
    vm.isDragging = false;
    if (file) vm._setFile(file);
  });
};
```

---

## Changing the API URL

Edit `APP_CONFIG` in `frontend/js/onboardingService.js`:

```javascript
.constant('APP_CONFIG', {
  apiBase: 'http://localhost:8080/api/onboard'   // ← change this
})
```

Per-environment overrides (dev / staging / prod) can be done by injecting
`APP_CONFIG` from a separate environment file.

---

## Styling

All styles live in `frontend/css/app.css`.  The file uses **CSS custom
properties** (variables) at the top so brand colours are easy to change:

```css
:root {
  --clr-primary:    #4f46e5;   /* indigo */
  --clr-success:    #16a34a;   /* green  */
  --clr-danger:     #dc2626;   /* red    */
}
```

Key CSS classes:

| Class | Purpose |
|-------|---------|
| `.kpi-tile` | KPI stat box (compose with `.kpi-total / .kpi-success / …`) |
| `.progress-fill-processing` | Shimmer animation |
| `.progress-fill-completed` | Solid green fill |
| `.state-badge` + `.badge-*` | Coloured state pill |
| `.drop-zone.drag-over` | Blue border on drag |
| `.drop-zone.file-selected` | Green border once a file is chosen |
| `.reason-tag` | Red pill for failure reasons in the table |

---

## Browser Support

| Browser | SSE | Fallback |
|---------|-----|---------|
| Chrome 90+ | ✅ | — |
| Firefox 88+ | ✅ | — |
| Safari 14+ | ✅ | — |
| Edge 90+ | ✅ | — |
| IE 11 | ❌ | Polling (1 s) |

---

## Adding a New Column to the Dashboard

1. Add the field to `OnboardingController.buildStatusPayload()` in the backend.
2. In `index.html`, add an expression inside the progress card:
   ```html
   <div>{{ vm.activeJob.yourNewField }}</div>
   ```
3. No service changes needed — the SSE payload is already the full job object.
