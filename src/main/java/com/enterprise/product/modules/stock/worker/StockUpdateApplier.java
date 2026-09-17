package com.enterprise.product.modules.stock.worker;

import com.enterprise.product.model.Product;
import com.enterprise.product.modules.product.ProductRepository;
import com.enterprise.product.modules.stock.StockUpdateHistoryRepository;
import com.enterprise.product.modules.stock.model.StockUpdateHistory;
import com.enterprise.product.modules.stock.model.StockUpdateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional core of the stock DB sync. Kept in its own bean so
 * Spring AOP proxies the @Transactional boundary correctly (avoiding
 * the self-invocation pitfall).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockUpdateApplier {

    private final ProductRepository productRepository;
    private final StockUpdateHistoryRepository historyRepository;

    /**
     * @return true if applied (or already applied via idempotency), false if
     *         a version conflict occurred and caller should retry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean apply(StockUpdateTask task) {
        // 1) Idempotency guard
        if (historyRepository.existsByReferenceId(task.getReferenceId())) {
            log.info("Idempotent skip: referenceId={} already applied", task.getReferenceId());
            return true;
        }

        Product product = productRepository.findById(task.getProductId())
                .orElseThrow(() -> new IllegalStateException(
                        "Product vanished: " + task.getProductId()));

        long before = product.getStock();
        long after;
        if (task.getUpdateType() == StockUpdateType.DECREMENT) {
            if (before < task.getDelta()) {
                log.error("DB stock would go negative for product {}. before={} delta={} ref={}",
                        task.getProductId(), before, task.getDelta(), task.getReferenceId());
                // Non-retryable; the caller should surface this as a failed task.
                throw new IllegalStateException(
                        "Negative stock prevented for product " + task.getProductId());
            }
            after = before - task.getDelta();
        } else {
            after = before + task.getDelta();
        }

        int updated = productRepository.updateStockIfVersionMatches(
                task.getProductId(), after, product.getVersion());
        if (updated == 0) {
            // Optimistic-lock conflict → let outer retry loop handle it
            throw new ObjectOptimisticLockingFailureException(Product.class, task.getProductId());
        }

        historyRepository.save(StockUpdateHistory.builder()
                .productId(task.getProductId())
                .updateType(task.getUpdateType())
                .delta(task.getDelta())
                .stockBefore(before)
                .stockAfter(after)
                .referenceId(task.getReferenceId())
                .reason(task.getReason())
                .createdBy(task.getCreatedBy())
                .build());

        log.debug("DB stock synced product={} {} delta={} {}→{} ref={}",
                task.getProductId(), task.getUpdateType(), task.getDelta(),
                before, after, task.getReferenceId());
        return true;
    }
}