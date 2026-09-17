package com.enterprise.product.modules.bulk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bulk_job", indexes = {
        @Index(name = "idx_bulk_job_status", columnList = "status"),
        @Index(name = "idx_bulk_job_type", columnList = "job_type"),
        @Index(name = "idx_bulk_job_created", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BulkJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private BulkJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BulkJobStatus status;

    @Column(name = "input_s3_key")
    private String inputS3Key;

    @Column(name = "output_s3_key")
    private String outputS3Key;

    @Column(name = "error_s3_key")
    private String errorS3Key;

    @Column(name = "total_rows")
    private long totalRows;

    @Column(name = "processed_rows")
    private long processedRows;

    @Column(name = "success_count")
    private long successCount;

    @Column(name = "failure_count")
    private long failureCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "submitted_by", length = 100)
    private String submittedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
