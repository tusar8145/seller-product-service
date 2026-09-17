package com.enterprise.product.modules.bulk;

import com.enterprise.product.modules.bulk.model.BulkJobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BulkJobMessage {
    private UUID jobId;
    private BulkJobType type;
    private String s3Key;
    private OffsetDateTime submittedAt;
}
