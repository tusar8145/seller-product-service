package com.enterprise.product.modules.product.consistency;

import com.enterprise.product.AbstractIntegrationTest;
import com.enterprise.product.config.RedisKeys;
import com.enterprise.product.modules.product.dto.ProductRequest;
import com.enterprise.product.modules.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ConsistencyApiIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired RedisTemplate<String, Object> redis;

    @Test
    void detects_missingInRedis() {
        ProductResponse p = rest.postForObject("/api/v1/products",
                ProductRequest.builder()
                        .name("Consistency IT")
                        .stock(5L)
                        .price(new BigDecimal("3.00"))
                        .build(),
                ProductResponse.class);

        // Evict from Redis to simulate drift
        redis.delete(RedisKeys.product(p.getId()));

        ConsistencyReport report = rest.getForObject(
                "/api/v1/products/cache/consistency?page=0&size=500",
                ConsistencyReport.class);

        assertThat(report).isNotNull();
        assertThat(report.getMissingInRedis()).isGreaterThanOrEqualTo(1);
        assertThat(report.getDetails())
                .anyMatch(d -> d.getProductId().equals(p.getId())
                        && d.getType().equals("MISSING_IN_REDIS"));
    }
}