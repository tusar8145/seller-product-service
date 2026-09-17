package com.enterprise.product.config;

/**
 * Central registry of all Redis key patterns used by this service.
 * Never build Redis keys inline - always reference here.
 */
public final class RedisKeys {
    private RedisKeys() {}

    // Product cache: product:{id}
    public static final String PRODUCT_PREFIX = "product:";
    public static String product(Long id) { return PRODUCT_PREFIX + id; }

    // Product list cache: product:list:{hash}
    public static final String PRODUCT_LIST_PREFIX = "product:list:";
    public static String productList(String hash) { return PRODUCT_LIST_PREFIX + hash; }

    // Stock cache (fast reservation layer): product:stock:{id}
    public static final String STOCK_PREFIX = "product:stock:";
    public static String stock(Long id) { return STOCK_PREFIX + id; }

    // Distributed lock: product:lock:stock:{id}
    public static final String LOCK_STOCK_PREFIX = "product:lock:stock:";
    public static String lockStock(Long id) { return LOCK_STOCK_PREFIX + id; }

    // Idempotency: product:idem:{requestId}
    public static final String IDEMPOTENCY_PREFIX = "product:idem:";
    public static String idempotency(String requestId) { return IDEMPOTENCY_PREFIX + requestId; }
}
