package com.enterprise.product.modules.stock;

import com.enterprise.product.AbstractIntegrationTest;
import com.enterprise.product.config.RedisKeys;
import com.enterprise.product.modules.product.dto.ProductRequest;
import com.enterprise.product.modules.product.dto.ProductResponse;
import com.enterprise.product.modules.stock.dto.StockUpdateRequest;
import com.enterprise.product.modules.stock.dto.StockUpdateResponse;
import com.enterprise.product.modules.stock.model.StockUpdateHistory;
import com.enterprise.product.modules.stock.model.StockUpdateType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class StockApiIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired StringRedisTemplate redis;
    @Autowired StockUpdateHistoryRepository historyRepo;

    @Test
    void decrement_reserves_inRedis_andSyncsToDb() {
        Long id = createProduct(100L);

        StockUpdateRequest req = StockUpdateRequest.builder()
                .updateType(StockUpdateType.DECREMENT)
                .quantity(30L)
                .referenceId(UUID.randomUUID().toString())
                .reason("IT")
                .build();

        StockUpdateResponse res = rest.postForObject(
                "/api/v1/products/" + id + "/stock", req, StockUpdateResponse.class);

        assertThat(res.isApplied()).isTrue();
        assertThat(res.getStockAfter()).isEqualTo(70L);
        assertThat(redis.opsForValue().get(RedisKeys.stock(id))).isEqualTo("70");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<StockUpdateHistory> h = historyRepo.findAll();
            assertThat(h).isNotEmpty();
            assertThat(h.get(0).getReferenceId()).isEqualTo(req.getReferenceId());
            assertThat(h.get(0).getStockAfter()).isEqualTo(70L);
        });
    }

    @Test
    void decrement_moreThanAvailable_rejected_noNegative() {
        Long id = createProduct(5L);

        StockUpdateRequest req = StockUpdateRequest.builder()
                .updateType(StockUpdateType.DECREMENT)
                .quantity(10L)
                .referenceId(UUID.randomUUID().toString())
                .build();

        var res = rest.postForEntity(
                "/api/v1/products/" + id + "/stock", req, String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(redis.opsForValue().get(RedisKeys.stock(id))).isEqualTo("5");
    }

    @Test
    void duplicate_referenceId_appliedOnlyOnce() {
        Long id = createProduct(10L);
        String ref = UUID.randomUUID().toString();

        StockUpdateRequest req = StockUpdateRequest.builder()
                .updateType(StockUpdateType.INCREMENT)
                .quantity(5L)
                .referenceId(ref)
                .build();

        StockUpdateResponse first = rest.postForObject(
                "/api/v1/products/" + id + "/stock", req, StockUpdateResponse.class);
        assertThat(first.isApplied()).isTrue();

        StockUpdateResponse replay = rest.postForObject(
                "/api/v1/products/" + id + "/stock", req, StockUpdateResponse.class);
        assertThat(replay.isApplied()).isFalse();

        assertThat(redis.opsForValue().get(RedisKeys.stock(id))).isEqualTo("15");
    }

    private Long createProduct(long stock) {
        ProductRequest req = ProductRequest.builder()
                .name("IT " + UUID.randomUUID())
                .stock(stock)
                .price(new BigDecimal("1.00"))
                .build();
        ProductResponse res = rest.postForObject("/api/v1/products", req, ProductResponse.class);
        return res.getId();
    }
}