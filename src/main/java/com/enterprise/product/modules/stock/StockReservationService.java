package com.enterprise.product.modules.stock;

import com.enterprise.product.common.exception.BusinessException;
import com.enterprise.product.common.exception.ResourceNotFoundException;
import com.enterprise.product.config.RedisKeys;
import com.enterprise.product.modules.product.ProductRepository;
import com.enterprise.product.modules.stock.dto.StockUpdateRequest;
import com.enterprise.product.modules.stock.dto.StockUpdateResponse;
import com.enterprise.product.modules.stock.model.StockUpdateType;
import com.enterprise.product.modules.stock.worker.StockUpdateTask;
import com.enterprise.product.modules.stock.worker.StockUpdateWorkerQueue;
import com.enterprise.product.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * Redis-first stock mutation. Handles:
 *  - idempotency via referenceId
 *  - distributed lock per product
 *  - atomic Lua-based validation + decrement/increment
 *  - enqueue for async DB synchronization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockReservationService {

    private static final Duration LOCK_WAIT = Duration.ofSeconds(2);
    private static final Duration LOCK_LEASE = Duration.ofSeconds(5);

    /**
     * Atomic Lua for decrement:
     *   KEYS[1] = stock key
     *   ARGV[1] = quantity
     * Returns: new stock value, or -1 if insufficient, or -2 if key missing.
     */
    private static final String DECREMENT_LUA = """
            local current = redis.call('GET', KEYS[1])
            if not current then return -2 end
            local qty = tonumber(ARGV[1])
            local cur = tonumber(current)
            if cur < qty then return -1 end
            return redis.call('DECRBY', KEYS[1], qty)
            """;

    /** Increment always succeeds if the key exists; if not, returns -2 so caller can rehydrate. */
    private static final String INCREMENT_LUA = """
            local current = redis.call('GET', KEYS[1])
            if not current then return -2 end
            return redis.call('INCRBY', KEYS[1], ARGV[1])
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final ProductRepository productRepository;
    private final StockUpdateWorkerQueue workerQueue;

    public StockUpdateResponse apply(Long productId, StockUpdateRequest request, String user) {
        // 0) Idempotency: has this exact referenceId been processed before?
        String idemKey = RedisKeys.idempotency(request.getReferenceId());
        Boolean firstSeen = stringRedisTemplate.opsForValue()
                .setIfAbsent(idemKey, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(firstSeen)) {
            log.info("Duplicate stock update request referenceId={} — ignored", request.getReferenceId());
            return StockUpdateResponse.builder()
                    .productId(productId)
                    .referenceId(request.getReferenceId())
                    .applied(false)
                    .message("Duplicate request ignored (idempotent)")
                    .build();
        }

        // 1) Acquire per-product distributed lock
        RLock lock = redissonClient.getLock(RedisKeys.lockStock(productId));
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT.toMillis(), LOCK_LEASE.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Interrupted while acquiring stock lock");
        }
        if (!locked) {
            throw new BusinessException("Could not acquire stock lock for product " + productId + ", retry shortly");
        }

        try {
            // 2) Ensure Redis has a stock key (rehydrate from DB on cold cache)
            ensureStockKey(productId);

            Long stockBefore = readStock(productId);
            if (stockBefore == null) {
                throw new ResourceNotFoundException("Stock not initialised for product " + productId);
            }

            // 3) Atomic validation + mutation
            long delta = request.getQuantity();
            String script = request.getUpdateType() == StockUpdateType.DECREMENT ? DECREMENT_LUA : INCREMENT_LUA;
            Long result = stringRedisTemplate.execute(
                    new DefaultRedisScript<>(script, Long.class),
                    Collections.singletonList(RedisKeys.stock(productId)),
                    String.valueOf(delta));

            if (result == null) {
                throw new BusinessException("Redis stock operation returned no result");
            }
            if (result == -2L) {
                throw new ResourceNotFoundException("Stock key missing for product " + productId);
            }
            if (result == -1L) {
                throw new BusinessException("Insufficient stock for product " + productId
                        + " (requested=" + delta + ", available=" + stockBefore + ")");
            }

            long stockAfter = result;

            // 4) Enqueue async DB persistence (idempotent, keyed by referenceId)
            workerQueue.enqueue(StockUpdateTask.builder()
                    .taskId(UUID.randomUUID().toString())
                    .productId(productId)
                    .updateType(request.getUpdateType())
                    .delta(delta)
                    .stockBefore(stockBefore)
                    .stockAfter(stockAfter)
                    .referenceId(request.getReferenceId())
                    .reason(request.getReason())
                    .createdBy(user)
                    .build());

            log.info("Stock {} product={} delta={} before={} after={} ref={}",
                    request.getUpdateType(), productId, delta, stockBefore, stockAfter, request.getReferenceId());

            return StockUpdateResponse.builder()
                    .productId(productId)
                    .updateType(request.getUpdateType())
                    .quantity(delta)
                    .stockBefore(stockBefore)
                    .stockAfter(stockAfter)
                    .referenceId(request.getReferenceId())
                    .applied(true)
                    .message("Stock updated (DB sync queued)")
                    .build();

        } finally {
            try { lock.unlock(); } catch (Exception ignore) {}
        }
    }

    // ─────────────────────────────────────────────
    private void ensureStockKey(Long productId) {
        String key = RedisKeys.stock(productId);
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) return;

        // Cold cache → load from DB and seed Redis. Use a lightweight query.
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        stringRedisTemplate.opsForValue().set(key, String.valueOf(product.getStock()), Duration.ofHours(24));
        log.debug("Rehydrated stock key for product {} = {}", productId, product.getStock());
    }

    private Long readStock(Long productId) {
        String v = stringRedisTemplate.opsForValue().get(RedisKeys.stock(productId));
        return v == null ? null : Long.valueOf(v);
    }

    /** Directly seed/overwrite Redis stock — used by create/update/bulk paths. */
    public void seedStock(Long productId, long stock) {
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeys.stock(productId), String.valueOf(stock), Duration.ofHours(24));
        } catch (Exception ex) {
            log.warn("seedStock failed for product {}: {}", productId, ex.getMessage());
        }
    }
}
