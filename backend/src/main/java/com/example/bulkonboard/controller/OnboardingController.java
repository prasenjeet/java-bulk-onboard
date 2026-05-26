package com.example.bulkonboard.controller;

import com.example.bulkonboard.model.JobStatus;
import com.example.bulkonboard.model.UploadResponse;
import com.example.bulkonboard.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller exposing:
 *   POST   /api/onboard/upload          – upload a CSV, returns jobId
 *   GET    /api/onboard/jobs/{jobId}     – poll job status (JSON)
 *   GET    /api/onboard/jobs/{jobId}/sse – Server-Sent Events stream
 *   GET    /api/onboard/jobs             – list all jobs
 *   GET    /api/onboard/template         – download blank CSV template
 */
@Slf4j
@RestController
@RequestMapping("/api/onboard")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final ExecutorService ssePool = Executors.newCachedThreadPool();

    // ------------------------------------------------------------------ //
    // 1. Upload CSV                                                        //
    // ------------------------------------------------------------------ //

    @PostMapping("/upload")
    public ResponseEntity<?> uploadCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No file selected. Please choose a CSV file to upload."));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid file type. Only .csv files are accepted."));
        }

        try {
            JobStatus job = onboardingService.submitJob(file);
            return ResponseEntity.ok(
                    new UploadResponse(job.getJobId(),
                            "CSV uploaded successfully. Processing " + job.getTotalRecords() + " record(s).",
                            job.getTotalRecords()));
        } catch (Exception e) {
            log.error("CSV upload failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process CSV: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ //
    // 2. Poll job status (JSON)                                            //
    // ------------------------------------------------------------------ //

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        return onboardingService.getJob(jobId)
                .<ResponseEntity<?>>map(job -> ResponseEntity.ok(buildStatusPayload(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------------ //
    // 3. Server-Sent Events stream                                         //
    // ------------------------------------------------------------------ //

    @GetMapping(value = "/jobs/{jobId}/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobStatus(@PathVariable String jobId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5-min timeout

        ssePool.execute(() -> {
            try {
                while (true) {
                    var optJob = onboardingService.getJob(jobId);
                    if (optJob.isEmpty()) {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(Map.of("error", "Job not found: " + jobId)));
                        emitter.complete();
                        return;
                    }

                    JobStatus job = optJob.get();
                    Map<String, Object> payload = buildStatusPayload(job);
                    emitter.send(SseEmitter.event().name("progress").data(payload));

                    if (job.getState() == JobStatus.State.COMPLETED
                            || job.getState() == JobStatus.State.FAILED) {
                        emitter.complete();
                        return;
                    }

                    Thread.sleep(500); // push update every 500 ms
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(e);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ------------------------------------------------------------------ //
    // 4. List all jobs                                                     //
    // ------------------------------------------------------------------ //

    @GetMapping("/jobs")
    public ResponseEntity<List<Map<String, Object>>> listJobs() {
        var jobs = onboardingService.getAllJobs().stream()
                .map(this::buildStatusPayload)
                .toList();
        return ResponseEntity.ok(jobs);
    }

    // ------------------------------------------------------------------ //
    // 5. Download blank CSV template                                       //
    // ------------------------------------------------------------------ //

    @GetMapping(value = "/template", produces = "text/csv")
    public ResponseEntity<String> downloadTemplate() {
        String csv = "account_id,account_name,email,phone,account_type,country,currency,credit_limit,status\n"
                   + "ACC001,John Doe,john.doe@example.com,+1-555-0100,PERSONAL,US,USD,5000.00,ACTIVE\n"
                   + "ACC002,Acme Corp,billing@acme.com,+1-555-0200,BUSINESS,US,USD,50000.00,ACTIVE\n";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"onboard_template.csv\"")
                .body(csv);
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                              //
    // ------------------------------------------------------------------ //

    private Map<String, Object> buildStatusPayload(JobStatus job) {
        return Map.ofEntries(
                Map.entry("jobId",               job.getJobId()),
                Map.entry("fileName",            job.getFileName()),
                Map.entry("state",               job.getState().name()),
                Map.entry("totalRecords",        job.getTotalRecords()),
                Map.entry("processedCount",      job.getProcessedCount()),
                Map.entry("successCount",        job.getSuccessCount()),
                Map.entry("failureCount",        job.getFailureCount()),
                Map.entry("percentageComplete",  job.getPercentageComplete()),
                Map.entry("percentageRemaining", job.getPercentageRemaining()),
                Map.entry("startedAt",           job.getStartedAt().toString()),
                Map.entry("completedAt",         job.getCompletedAt() != null
                                                    ? job.getCompletedAt().toString() : ""),
                Map.entry("errorMessage",        job.getErrorMessage() != null
                                                    ? job.getErrorMessage() : ""),
                Map.entry("failures",            job.getFailures())
        );
    }
}
