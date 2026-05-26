package com.example.bulkonboard.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Returned immediately after a CSV is uploaded; the client polls /api/jobs/{jobId}.
 */
@Data
@AllArgsConstructor
public class UploadResponse {
    private String jobId;
    private String message;
    private int totalRecords;
}
