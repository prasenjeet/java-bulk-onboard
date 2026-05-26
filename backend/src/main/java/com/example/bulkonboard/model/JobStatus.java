package com.example.bulkonboard.model;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe snapshot of an active or completed onboarding job.
 */
@Data
public class JobStatus {

    public enum State { QUEUED, PROCESSING, COMPLETED, FAILED }

    private final String jobId;
    private final String fileName;
    private final Instant startedAt = Instant.now();
    private Instant completedAt;

    private volatile int totalRecords;
    private final AtomicInteger processedCount  = new AtomicInteger(0);
    private final AtomicInteger successCount    = new AtomicInteger(0);
    private final AtomicInteger failureCount    = new AtomicInteger(0);

    private volatile State state = State.QUEUED;
    private volatile String errorMessage;

    // Ordered list of failure details for the UI table
    private final List<ProcessingResult> failures = new ArrayList<>();

    // ------------------------------------------------------------------ //
    // Convenience accessors used by controller / SSE publisher            //
    // ------------------------------------------------------------------ //

    public int getProcessedCount()  { return processedCount.get(); }
    public int getSuccessCount()    { return successCount.get(); }
    public int getFailureCount()    { return failureCount.get(); }

    public double getPercentageComplete() {
        if (totalRecords == 0) return 0.0;
        return Math.round((processedCount.get() * 100.0 / totalRecords) * 10) / 10.0;
    }

    public double getPercentageRemaining() {
        return Math.round((100.0 - getPercentageComplete()) * 10) / 10.0;
    }

    public synchronized void recordSuccess() {
        processedCount.incrementAndGet();
        successCount.incrementAndGet();
    }

    public synchronized void recordFailure(ProcessingResult result) {
        processedCount.incrementAndGet();
        failureCount.incrementAndGet();
        failures.add(result);
    }
}
