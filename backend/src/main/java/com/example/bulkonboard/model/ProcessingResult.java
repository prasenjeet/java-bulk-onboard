package com.example.bulkonboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds the result of processing a single account record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingResult {

    private String accountId;
    private String accountName;
    private String email;
    private boolean success;
    private String failureReason;
    private long processingTimeMs;
}
