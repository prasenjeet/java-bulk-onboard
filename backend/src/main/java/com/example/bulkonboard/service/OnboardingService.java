package com.example.bulkonboard.service;

import com.example.bulkonboard.model.AccountRecord;
import com.example.bulkonboard.model.JobStatus;
import com.example.bulkonboard.model.ProcessingResult;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OnboardingService {

    private static final Pattern EMAIL_REGEX =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Set<String> VALID_ACCOUNT_TYPES =
            Set.of("PERSONAL", "BUSINESS", "CORPORATE", "SAVINGS", "CHECKING");

    private static final Set<String> VALID_CURRENCIES =
            Set.of("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "INR", "SGD", "AED", "CHF");

    @Value("${app.onboarding.processing-delay-ms:300}")
    private long processingDelayMs;

    // In-memory job store – replace with a persistent store for production
    private final Map<String, JobStatus> jobStore = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ //
    // Public API                                                           //
    // ------------------------------------------------------------------ //

    /**
     * Parses the uploaded CSV, stores a JobStatus, and kicks off async processing.
     * Returns the job immediately so the controller can respond without blocking.
     */
    public JobStatus submitJob(MultipartFile file) throws Exception {
        String jobId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();

        List<AccountRecord> records = parseCsv(file);

        JobStatus job = new JobStatus(jobId, fileName);
        job.setTotalRecords(records.size());
        jobStore.put(jobId, job);

        // Kick off async processing – method returns immediately
        processAsync(job, records);

        return job;
    }

    /**
     * Returns a live snapshot of the job, or empty if unknown.
     */
    public Optional<JobStatus> getJob(String jobId) {
        return Optional.ofNullable(jobStore.get(jobId));
    }

    /**
     * Returns all jobs (most-recent first).
     */
    public List<JobStatus> getAllJobs() {
        List<JobStatus> list = new ArrayList<>(jobStore.values());
        list.sort(Comparator.comparing(JobStatus::getStartedAt).reversed());
        return list;
    }

    // ------------------------------------------------------------------ //
    // Internals                                                            //
    // ------------------------------------------------------------------ //

    private List<AccountRecord> parseCsv(MultipartFile file) throws Exception {
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return new CsvToBeanBuilder<AccountRecord>(reader)
                    .withType(AccountRecord.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withIgnoreEmptyLine(true)
                    .build()
                    .parse();
        }
    }

    @Async
    protected void processAsync(JobStatus job, List<AccountRecord> records) {
        job.setState(JobStatus.State.PROCESSING);
        log.info("Starting job {} – {} records", job.getJobId(), records.size());

        try {
            for (AccountRecord record : records) {
                long t0 = System.currentTimeMillis();
                List<String> errors = validate(record);

                // Simulate network / DB latency
                Thread.sleep(processingDelayMs);

                if (errors.isEmpty()) {
                    // Simulate ~10% random backend failures
                    if (Math.random() < 0.10) {
                        errors.add("Downstream system error: account creation timeout");
                    }
                }

                if (errors.isEmpty()) {
                    job.recordSuccess();
                    log.debug("Job {} – SUCCESS for account {}", job.getJobId(), record.getAccountId());
                } else {
                    ProcessingResult failure = ProcessingResult.builder()
                            .accountId(record.getAccountId())
                            .accountName(record.getAccountName())
                            .email(record.getEmail())
                            .success(false)
                            .failureReason(String.join("; ", errors))
                            .processingTimeMs(System.currentTimeMillis() - t0)
                            .build();
                    job.recordFailure(failure);
                    log.debug("Job {} – FAILED for account {}: {}", job.getJobId(), record.getAccountId(), failure.getFailureReason());
                }
            }

            job.setState(JobStatus.State.COMPLETED);
            job.setCompletedAt(java.time.Instant.now());
            log.info("Job {} complete – {} success, {} failed",
                    job.getJobId(), job.getSuccessCount(), job.getFailureCount());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.setState(JobStatus.State.FAILED);
            job.setErrorMessage("Processing interrupted");
        } catch (Exception e) {
            job.setState(JobStatus.State.FAILED);
            job.setErrorMessage("Unexpected error: " + e.getMessage());
            log.error("Job {} failed with error", job.getJobId(), e);
        }
    }

    private List<String> validate(AccountRecord r) {
        List<String> errors = new ArrayList<>();

        if (StringUtils.isBlank(r.getAccountId()))
            errors.add("account_id is required");

        if (StringUtils.isBlank(r.getAccountName()))
            errors.add("account_name is required");

        if (StringUtils.isBlank(r.getEmail())) {
            errors.add("email is required");
        } else if (!EMAIL_REGEX.matcher(r.getEmail().trim()).matches()) {
            errors.add("email format is invalid: " + r.getEmail());
        }

        if (StringUtils.isBlank(r.getPhone()))
            errors.add("phone is required");

        if (StringUtils.isBlank(r.getAccountType())) {
            errors.add("account_type is required");
        } else if (!VALID_ACCOUNT_TYPES.contains(r.getAccountType().toUpperCase())) {
            errors.add("account_type must be one of " + VALID_ACCOUNT_TYPES + "; got: " + r.getAccountType());
        }

        if (StringUtils.isBlank(r.getCountry()))
            errors.add("country is required");

        if (StringUtils.isBlank(r.getCurrency())) {
            errors.add("currency is required");
        } else if (!VALID_CURRENCIES.contains(r.getCurrency().toUpperCase())) {
            errors.add("currency must be one of " + VALID_CURRENCIES + "; got: " + r.getCurrency());
        }

        if (StringUtils.isNotBlank(r.getCreditLimit())) {
            try {
                double limit = Double.parseDouble(r.getCreditLimit());
                if (limit < 0)
                    errors.add("credit_limit cannot be negative");
            } catch (NumberFormatException e) {
                errors.add("credit_limit must be numeric; got: " + r.getCreditLimit());
            }
        }

        return errors;
    }
}
