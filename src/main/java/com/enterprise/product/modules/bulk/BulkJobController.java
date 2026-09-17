package com.enterprise.product.modules.bulk;

import com.enterprise.product.common.dto.ApiResponse;
import com.enterprise.product.common.exception.ResourceNotFoundException;
import com.enterprise.product.modules.bulk.model.BulkJob;
import com.enterprise.product.modules.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/bulk")
@RequiredArgsConstructor
public class BulkJobController {

    private final BulkJobService bulkJobService;
    private final BulkJobRepository jobRepository;
    private final S3StorageService s3;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UUID>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User", defaultValue = "anonymous") String user) {
        UUID jobId = bulkJobService.submitImport(file, user);
        return ResponseEntity.accepted()
                .body(ApiResponse.ok(jobId, "Bulk product import accepted, job id: " + jobId));
    }

    @PostMapping(value = "/stock-update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UUID>> stockUpdate(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User", defaultValue = "anonymous") String user) {
        UUID jobId = bulkJobService.submitStockUpdate(file, user);
        return ResponseEntity.accepted()
                .body(ApiResponse.ok(jobId, "Bulk stock update accepted., job id: " + jobId));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<BulkJob>> status(@PathVariable UUID jobId) {
        BulkJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        return ResponseEntity.ok(ApiResponse.ok(job, "Job status"));
    }

    @GetMapping("/{jobId}/failures-url")
    public ResponseEntity<ApiResponse<String>> failuresUrl(@PathVariable UUID jobId) {
        BulkJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        if (job.getErrorS3Key() == null) {
            return ResponseEntity.ok(ApiResponse.ok(null, "No failures recorded"));
        }
        String url = s3.generatePresignedUrl(job.getErrorS3Key(), Duration.ofHours(1));
        return ResponseEntity.ok(ApiResponse.ok(url, "Presigned download URL (1h)"));
    }
}
