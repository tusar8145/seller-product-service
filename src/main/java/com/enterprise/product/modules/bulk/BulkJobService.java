package com.enterprise.product.modules.bulk;

import com.enterprise.product.config.KafkaTopics;
import com.enterprise.product.modules.bulk.model.BulkJob;
import com.enterprise.product.modules.bulk.model.BulkJobStatus;
import com.enterprise.product.modules.bulk.model.BulkJobType;
import com.enterprise.product.modules.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkJobService {

    private final S3StorageService s3StorageService;
    private final BulkJobRepository jobRepository;
    private final KafkaTemplate<String, BulkJobMessage> bulkKafkaTemplate;

    @Transactional
    public UUID submitImport(MultipartFile file, String submittedBy) {
        UUID jobId = UUID.randomUUID();
        String s3Key = "bulk-import/" + jobId + "/" + file.getOriginalFilename();

        s3StorageService.uploadInput(file, s3Key);

        BulkJob job = BulkJob.builder()
                .id(jobId)
                .jobType(BulkJobType.IMPORT)
                .status(BulkJobStatus.PENDING)
                .inputS3Key(s3Key)
                .submittedBy(submittedBy)
                .build();
        jobRepository.save(job);

        bulkKafkaTemplate.send(KafkaTopics.PRODUCT_BULK_IMPORT, jobId.toString(),
                BulkJobMessage.builder()
                        .jobId(jobId)
                        .type(BulkJobType.IMPORT)
                        .s3Key(s3Key)
                        .submittedAt(OffsetDateTime.now())
                        .build());

        log.info("Bulk import job {} submitted, s3Key={}", jobId, s3Key);
        return jobId;
    }

    @Transactional
    public UUID submitStockUpdate(MultipartFile file, String submittedBy) {
        UUID jobId = UUID.randomUUID();
        String s3Key = "bulk-stock/" + jobId + "/" + file.getOriginalFilename();

        s3StorageService.uploadInput(file, s3Key);

        BulkJob job = BulkJob.builder()
                .id(jobId)
                .jobType(BulkJobType.STOCK_UPDATE)
                .status(BulkJobStatus.PENDING)
                .inputS3Key(s3Key)
                .submittedBy(submittedBy)
                .build();
        jobRepository.save(job);

        bulkKafkaTemplate.send(KafkaTopics.PRODUCT_BULK_IMPORT, jobId.toString(),
                BulkJobMessage.builder()
                        .jobId(jobId)
                        .type(BulkJobType.STOCK_UPDATE)
                        .s3Key(s3Key)
                        .submittedAt(OffsetDateTime.now())
                        .build());

        log.info("Bulk stock-update job {} submitted, s3Key={}", jobId, s3Key);
        return jobId;
    }
}
