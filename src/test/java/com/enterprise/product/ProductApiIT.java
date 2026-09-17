package com.enterprise.product;

import com.enterprise.product.config.RedisKeys;
import com.enterprise.product.modules.product.dto.ProductRequest;
import com.enterprise.product.modules.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProductApiIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired RedisTemplate<String, Object> redis;

    @Test
    void create_then_get_isServedFromRedis() {
        // 1. Create
        ProductRequest req = ProductRequest.builder()
                .name("IT Product")
                .details("from integration test")
                .stock(50L)
                .price(new BigDecimal("12.34"))
                .build();

        ResponseEntity<ProductResponse> createRes =
                rest.postForEntity("/api/v1/products", req, ProductResponse.class);

        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = createRes.getBody().getId();

        // 2. Create seeds Redis
        Object cached = redis.opsForValue().get(RedisKeys.product(id));
        assertThat(cached).isNotNull();

        // 3. Evict to prove the read path backfills
        redis.delete(RedisKeys.product(id));
        assertThat(redis.opsForValue().get(RedisKeys.product(id))).isNull();

        // 4. GET should repopulate
        ResponseEntity<ProductResponse> getRes =
                rest.getForEntity("/api/v1/products/" + id, ProductResponse.class);

        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRes.getBody().getName()).isEqualTo("IT Product");

        Object backfilled = redis.opsForValue().get(RedisKeys.product(id));
        assertThat(backfilled).isNotNull();
    }

    @Test
    void get_unknownId_returns404() {
        ResponseEntity<String> res =
                rest.getForEntity("/api/v1/products/999999", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}