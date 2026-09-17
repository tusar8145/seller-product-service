package com.enterprise.product.modules.bulk.consumer;

import com.enterprise.product.config.KafkaTopics;
import com.enterprise.product.modules.bulk.BulkJobMessage;
import com.enterprise.product.modules.bulk.BulkJobRepository;
import com.enterprise.product.modules.bulk.ExcelBatchValidator;
import com.enterprise.product.modules.bulk.ExcelFailure;
import com.enterprise.product.modules.bulk.ExcelReader;
import com.enterprise.product.modules.bulk.ExcelRow;
import com.enterprise.product.modules.bulk.ExcelSchema;
import com.enterprise.product.modules.bulk.ExcelWriter;
import com.enterprise.product.modules.bulk.model.BulkJob;
import com.enterprise.product.modules.bulk.model.BulkJobStatus;
import com.enterprise.product.modules.bulk.model.BulkJobType;
import com.enterprise.product.modules.product.ProductRepository;
import com.enterprise.product.modules.stock.StockReservationService;
import com.enterprise.product.modules.stock.model.StockUpdateType;
import com.enterprise.product.model.Product;
import com.enterprise.product.modules.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class BulkImportConsumer {

    private final S3StorageService s3;
    private final ExcelReader excelReader;
    private final ExcelWriter excelWriter;
    private final ExcelBatchValidator validator;
    private final ProductRepository productRepository;
    private final BulkJobRepository jobRepository;
    private final StockReservationService stockReservationService;

    private static final int DB_BATCH_SIZE = 500;

    @KafkaListener(
            topics = KafkaTopics.PRODUCT_BULK_IMPORT,
            containerFactory = "bulkKafkaListenerContainerFactory")
    public void onBulkJob(@Payload BulkJobMessage msg, Acknowledgment ack) {
        UUID jobId = msg.getJobId();
        log.info("🚀 Starting bulk job {} type={} key={}", jobId, msg.getType(), msg.getS3Key());

        BulkJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

        job.setStatus(BulkJobStatus.RUNNING);
        jobRepository.save(job);

        AtomicLong success = new AtomicLong();
        AtomicLong failure = new AtomicLong();
        List<ExcelFailure> failures = java.util.Collections.synchronizedList(new ArrayList<>());

       try (InputStream in = s3.download(msg.getS3Key())) {
    if (msg.getType() == BulkJobType.IMPORT) {
        excelReader.streamRows(in, ExcelSchema.PRODUCT_CREATE,
                batch -> processImportBatch(batch, failures, success, failure));
    } else if (msg.getType() == BulkJobType.STOCK_UPDATE) {
        excelReader.streamRows(in, ExcelSchema.STOCK_UPDATE,
                batch -> processStockBatch(batch, failures, success, failure, jobId));
    }

            if (!failures.isEmpty()) {
                byte[] failureXlsx = excelWriter.writeFailures(failures);
                String errorKey = "bulk-import/" + jobId + "/failures.xlsx";
                s3.uploadOutput(failureXlsx, errorKey,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                job.setErrorS3Key(errorKey);
            }

            job.setSuccessCount(success.get());
            job.setFailureCount(failure.get());
            job.setProcessedRows(success.get() + failure.get());
            job.setStatus(failure.get() == 0 ? BulkJobStatus.COMPLETED : BulkJobStatus.PARTIAL_FAILURE);
            jobRepository.save(job);

            log.info("✅ Bulk job {} finished: success={}, failure={}", jobId, success.get(), failure.get());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("❌ Bulk job {} failed", jobId, e);
            job.setStatus(BulkJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
            throw new RuntimeException(e);
        }
    }

    private void processImportBatch(List<ExcelRow> batch,
                                    List<ExcelFailure> failures,
                                    AtomicLong success,
                                    AtomicLong failure) {
        List<ExcelRow> valid = new ArrayList<>(batch.size());
        for (ExcelRow row : batch) {
            List<String> errors = validator.validateRowForCreate(row);
            if (errors.isEmpty()) valid.add(row);
            else {
                failures.add(ExcelFailure.builder()
                        .rowNumber(row.getRowNumber())
                        .identifier(row.getName())
                        .errorMessage(String.join("; ", errors))
                        .build());
                failure.incrementAndGet();
            }
        }
        if (valid.isEmpty()) return;

        List<Product> entities = valid.stream().map(r -> Product.builder()
                .name(r.getName())
                .details(r.getDetails())
                .image(r.getImage())
                .stock(r.getStock())
                .price(r.getPrice())
                .build()).toList();

        try {
            List<Product> saved = productRepository.saveAll(entities);
            productRepository.flush();
            success.addAndGet(saved.size());
            // seed Redis for each
            for (Product p : saved) {
                stockReservationService.seedStock(p.getId(), p.getStock());
            }
        } catch (Exception ex) {
            log.warn("Batch insert failed, falling back to per-row: {}", ex.getMessage());
            for (int i = 0; i < entities.size(); i++) {
                try {
                    Product p = productRepository.saveAndFlush(entities.get(i));
                    stockReservationService.seedStock(p.getId(), p.getStock());
                    success.incrementAndGet();
                } catch (Exception rowEx) {
                    ExcelRow r = valid.get(i);
                    failures.add(ExcelFailure.builder()
                            .rowNumber(r.getRowNumber())
                            .identifier(r.getName())
                            .errorMessage(rowEx.getMessage())
                            .build());
                    failure.incrementAndGet();
                }
            }
        }
    }

    private void processStockBatch(List<ExcelRow> batch,
                                   List<ExcelFailure> failures,
                                   AtomicLong success,
                                   AtomicLong failure,
                                   UUID jobId) {
        for (ExcelRow row : batch) {
            List<String> errors = validator.validateRowForStockUpdate(row);
            if (!errors.isEmpty()) {
                failures.add(ExcelFailure.builder()
                        .rowNumber(row.getRowNumber())
                        .identifier(String.valueOf(row.getId()))
                        .errorMessage(String.join("; ", errors))
                        .build());
                failure.incrementAndGet();
                continue;
            }

            try {
                long delta = row.getStock(); // may be negative
                StockUpdateType type = delta >= 0 ? StockUpdateType.INCREMENT : StockUpdateType.DECREMENT;
                long qty = Math.abs(delta);

                // referenceId per row per job → idempotent on replay
                String refId = "bulk-" + jobId + "-row-" + row.getRowNumber();

                stockReservationService.apply(row.getId(),
                        com.enterprise.product.modules.stock.dto.StockUpdateRequest.builder()
                                .updateType(type)
                                .quantity(qty)
                                .referenceId(refId)
                                .reason("BULK_STOCK_UPDATE")
                                .build(),
                        "bulk-job");

                success.incrementAndGet();
            } catch (Exception ex) {
                failures.add(ExcelFailure.builder()
                        .rowNumber(row.getRowNumber())
                        .identifier(String.valueOf(row.getId()))
                        .errorMessage(ex.getMessage())
                        .build());
                failure.incrementAndGet();
            }
        }
    }
}
