package com.enterprise.product.modules.stock;

import com.enterprise.product.common.exception.BusinessException;
import com.enterprise.product.config.RedisKeys;
import com.enterprise.product.modules.product.ProductRepository;
import com.enterprise.product.modules.stock.dto.StockUpdateRequest;
import com.enterprise.product.modules.stock.dto.StockUpdateResponse;
import com.enterprise.product.modules.stock.model.StockUpdateType;
import com.enterprise.product.modules.stock.worker.StockUpdateTask;
import com.enterprise.product.modules.stock.worker.StockUpdateWorkerQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTest {

    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock RedissonClient redissonClient;
    @Mock ProductRepository productRepository;
    @Mock StockUpdateWorkerQueue workerQueue;

    @Mock ValueOperations<String, String> valueOps;
    @Mock RLock lock;

    @InjectMocks StockReservationService service;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void decrement_success_reserves_and_enqueues() throws Exception {
        Long productId = 1L;
        String stockKey = RedisKeys.stock(productId);
        String idemKey  = RedisKeys.idempotency("ref-1");

        when(valueOps.setIfAbsent(eq(idemKey), eq("1"), any())).thenReturn(true);
        when(redissonClient.getLock(RedisKeys.lockStock(productId))).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(stringRedisTemplate.hasKey(stockKey)).thenReturn(true);
        when(valueOps.get(stockKey)).thenReturn("10");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), eq("3"))).thenReturn(7L);

        StockUpdateRequest req = StockUpdateRequest.builder()
                .updateType(StockUpdateType.DECREMENT)
                .quantity(3L)
                .referenceId("ref-1")
                .build();

        StockUpdateResponse res = service.apply(productId, req, "tester");

        assertThat(res.isApplied()).isTrue();
        assertThat(res.getStockBefore()).isEqualTo(10L);
        assertThat(res.getStockAfter()).isEqualTo(7L);

        ArgumentCaptor<StockUpdateTask> captor = ArgumentCaptor.forClass(StockUpdateTask.class);
        verify(workerQueue).enqueue(captor.capture());
        assertThat(captor.getValue().getDelta()).isEqualTo(3L);
        assertThat(captor.getValue().getReferenceId()).isEqualTo("ref-1");

        verify(lock).unlock();
    }

    @Test
    void decrement_insufficient_stock_throws_noEnqueue() throws Exception {
        Long productId = 2L;
        String stockKey = RedisKeys.stock(productId);
        String idemKey  = RedisKeys.idempotency("ref-2");

        when(valueOps.setIfAbsent(eq(idemKey), eq("1"), any())).thenReturn(true);
        when(redissonClient.getLock(RedisKeys.lockStock(productId))).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(stringRedisTemplate.hasKey(stockKey)).thenReturn(true);
        when(valueOps.get(stockKey)).thenReturn("2");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), eq("5"))).thenReturn(-1L);

        StockUpdateRequest req = StockUpdateRequest.builder()
                .updateType(StockUpdateType.DECREMENT)
                .quantity(5L)
                .referenceId("ref-2")
                .build();

        assertThatThrownBy(() -> service.apply(productId, req, "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock");

        verify(workerQueue, never()).enqueue(any());
        verify(lock).unlock();
    }

    @Test
    void duplicate_referenceId_is_noop() {
        String ref = "ref-dup";
        when(valueOps.setIfAbsent(eq(RedisKeys.idempotency(ref)), eq("1"), any())).thenReturn(false);

        StockUpdateRequest req = StockUpdateRequest.builder()
                .updateType(StockUpdateType.INCREMENT)
                .quantity(1L)
                .referenceId(ref)
                .build();

        StockUpdateResponse res = service.apply(99L, req, "tester");

        assertThat(res.isApplied()).isFalse();
        assertThat(res.getMessage()).containsIgnoringCase("duplicate");

        verify(workerQueue, never()).enqueue(any());
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    void lock_timeout_throws() throws Exception {
        Long productId = 3L;
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(true);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);

        StockUpdateRequest req = StockUpdateRequest.builder()
                .updateType(StockUpdateType.INCREMENT)
                .quantity(1L)
                .referenceId("ref-lock")
                .build();

        assertThatThrownBy(() -> service.apply(productId, req, "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Could not acquire stock lock");
    }
}