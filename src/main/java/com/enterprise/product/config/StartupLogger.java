package com.enterprise.product.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final KafkaAdmin kafkaAdmin;

    @Value("${server.port:8080}")
    private int port;

    @Value("${spring.application.name:seller-product-service}")
    private String appName;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("\n" + line("=", 78) + "\n" +
                 "  Seller Product Service started successfully\n" +
                 line("=", 78));

        log.info("Application  : {} (profile={})", appName, System.getProperty("spring.profiles.active", "default"));
        log.info("Base URL     : http://localhost:{}", port);
        log.info("Swagger UI   : http://localhost:{}/swagger-ui.html", port);
        log.info("API Docs     : http://localhost:{}/v3/api-docs", port);

        // DB
        try (Connection c = dataSource.getConnection()) {
            log.info("Database connection: SUCCESS  (url={}, valid={})",
                    c.getMetaData().getURL(), c.isValid(2));
        } catch (Exception e) {
            log.error("Database connection: FAILED — {}", e.getMessage());
        }

        // Redis
        try {
            String pong = redisConnectionFactory.getConnection().ping();
            log.info("Redis connection: SUCCESS (ping={})", pong);
        } catch (Exception e) {
            log.error("Redis connection: FAILED — {}", e.getMessage());
        }

        // Kafka
        try {
            var desc = kafkaAdmin.describeTopics("product.events");
            log.info("Kafka connection: SUCCESS (topics visible={})", desc.keySet());
        } catch (Exception e) {
            log.error("Kafka connection: FAILED — {}", e.getMessage());
        }

        log.info("\nAvailable endpoints:\n" +
                 "  POST   /api/v1/products\n" +
                 "  GET    /api/v1/products/{id}\n" +
                 "  GET    /api/v1/products/{id}/from-master\n" +
                 "  GET    /api/v1/products\n" +
                 "  PUT    /api/v1/products/{id}\n" +
                 "  DELETE /api/v1/products/{id}\n" +
                 "  POST   /api/v1/products/{id}/stock\n" +
                 "  GET    /api/v1/products/{id}/stock/history\n" +
                 "  POST   /api/v1/products/bulk/import\n" +
                 "  POST   /api/v1/products/bulk/stock-update\n" +
                 "  GET    /api/v1/products/bulk/{jobId}\n" +
                 "  GET    /api/v1/products/bulk/{jobId}/failures-url\n" +
                 "  GET    /api/v1/products/cache/consistency\n" +
                 "  GET    /actuator/health\n");
    }

    private String line(String ch, int n) { return ch.repeat(n); }
}
