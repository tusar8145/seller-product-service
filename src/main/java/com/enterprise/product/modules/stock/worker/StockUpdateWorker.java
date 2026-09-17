package com.enterprise.product.modules.stock.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockUpdateWorker {

    private static final int MAX_RETRIES = 5;

    private final StockUpdateApplier applier;   // ← injected proxy  bean.

    public void process(StockUpdateTask task) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                applier.apply(task);            // ← proxied → @Transactional works
                return;
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.warn("Optimistic lock conflict on product {} (attempt {}/{}), retrying",
                        task.getProductId(), attempt, MAX_RETRIES);
            } catch (Exception ex) {
                log.error("Stock worker failed for task {} product {}: {}",
                        task.getTaskId(), task.getProductId(), ex.getMessage(), ex);
                return;
            }
            backoff(attempt);
        }
        log.error("Stock worker exhausted retries for task {} product {}",
                task.getTaskId(), task.getProductId());
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(50L * attempt, 500L));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}