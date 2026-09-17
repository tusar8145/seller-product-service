package com.enterprise.product.config;

/**
 * Central registry of all Kafka topics used by this service.
 */
public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String PRODUCT_EVENTS = "product.events";
    public static final String PRODUCT_BULK_IMPORT = "product.bulk.import";
    public static final String PRODUCT_BULK_IMPORT_DLQ = "product.bulk.import.dlq";
    public static final String PRODUCT_STOCK_UPDATE = "product.stock.update";
    public static final String PRODUCT_STOCK_UPDATE_DLQ = "product.stock.update.dlq";
}
