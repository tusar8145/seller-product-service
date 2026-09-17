package com.enterprise.product.modules.product;

import com.enterprise.product.common.exception.ResourceNotFoundException;
import com.enterprise.product.config.RedisKeys;
import com.enterprise.product.config.datasource.ReadFromMaster;
import com.enterprise.product.model.Product;
import com.enterprise.product.modules.kafka.ProductEventPublisher;
import com.enterprise.product.modules.product.dto.ProductRequest;
import com.enterprise.product.modules.product.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductServiceImpl implements ProductService {

    private static final Duration PRODUCT_TTL = Duration.ofMinutes(15);
    private static final Duration STOCK_TTL   = Duration.ofHours(24);

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductEventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;

    // ─────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────
    @Override
    public ProductResponse create(ProductRequest request) {
        Product entity = productMapper.toEntity(request);
        Product saved = productRepository.save(entity);

        cacheProduct(saved);
        cacheStock(saved.getId(), saved.getStock());

        log.info("Product created id={}", saved.getId());
        eventPublisher.publishProductCreated(saved);
        return productMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────
    // READ (Redis-first)
    // ─────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        // 1) Redis-first
        try {
            Object cached = redisTemplate.opsForValue().get(RedisKeys.product(id));
            if (cached instanceof ProductResponse pr) {
                log.debug("Cache HIT product id={}", id);
                return pr;
            }
        } catch (Exception ex) {
            log.warn("Redis read failed for product {} — falling back to DB: {}", id, ex.getMessage());
        }

        // 2) DB fallback (read replica)
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        ProductResponse response = productMapper.toResponse(product);

        // 3) Backfill Redis
        try {
            redisTemplate.opsForValue().set(RedisKeys.product(id), response, PRODUCT_TTL);
            cacheStock(id, product.getStock());
        } catch (Exception ex) {
            log.warn("Redis backfill failed for product {}: {}", id, ex.getMessage());
        }
        return response;
    }

    @Override
    @ReadFromMaster
    @Transactional(readOnly = true)
    public ProductResponse getByIdFromMaster(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> list(String keyword, Pageable pageable) {
        return productRepository.search(keyword, pageable).map(productMapper::toResponse);
    }

    // ─────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────
    @Override
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        productMapper.updateEntity(request, product);
        Product saved = productRepository.save(product);

        cacheProduct(saved);
        cacheStock(saved.getId(), saved.getStock());

        eventPublisher.publishProductUpdated(saved);
        return productMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────
    @Override
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        productRepository.delete(product);

        try {
            redisTemplate.delete(RedisKeys.product(id));
            redisTemplate.delete(RedisKeys.stock(id));
        } catch (Exception ex) {
            log.warn("Redis cleanup failed for product {}: {}", id, ex.getMessage());
        }
        eventPublisher.publishProductDeleted(id);
    }

    // ─────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────
    private void cacheProduct(Product product) {
        try {
            redisTemplate.opsForValue().set(
                    RedisKeys.product(product.getId()),
                    productMapper.toResponse(product),
                    PRODUCT_TTL);
        } catch (Exception ex) {
            log.warn("Failed to cache product {}: {}", product.getId(), ex.getMessage());
        }
    }

    private void cacheStock(Long id, Long stock) {
        try {
            redisTemplate.opsForValue().set(RedisKeys.stock(id), stock, STOCK_TTL);
        } catch (Exception ex) {
            log.warn("Failed to cache stock for product {}: {}", id, ex.getMessage());
        }
    }
}
