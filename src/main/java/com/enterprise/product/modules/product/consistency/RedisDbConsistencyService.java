package com.enterprise.product.modules.product.consistency;

import com.enterprise.product.config.RedisKeys;
import com.enterprise.product.model.Product;
import com.enterprise.product.modules.product.ProductRepository;
import com.enterprise.product.modules.product.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Batched comparison of Redis vs DB for the current page of products.
 * Does not load the whole table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisDbConsistencyService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional(readOnly = true)
    public ConsistencyReport compare(Pageable pageable) {
        Page<Product> page = productRepository.findAll(pageable);

        long matched = 0, mismatched = 0, missingInRedis = 0, missingInDb = 0;
        List<MismatchDetail> details = new ArrayList<>();

        for (Product db : page.getContent()) {
            Long id = db.getId();
            Object cached;
            try {
                cached = redisTemplate.opsForValue().get(RedisKeys.product(id));
            } catch (Exception ex) {
                log.warn("Redis read failed during consistency check for {}: {}", id, ex.getMessage());
                cached = null;
            }

            if (cached == null) {
                missingInRedis++;
                details.add(MismatchDetail.builder()
                        .productId(id).type("MISSING_IN_REDIS")
                        .fields(List.of("__all__"))
                        .redisValue(null)
                        .dbValue(db.getName())
                        .build());
                continue;
            }

            if (!(cached instanceof ProductResponse r)) {
                mismatched++;
                details.add(MismatchDetail.builder()
                        .productId(id).type("FIELD_MISMATCH")
                        .fields(List.of("__type__"))
                        .redisValue(cached.getClass().getSimpleName())
                        .dbValue(ProductResponse.class.getSimpleName())
                        .build());
                continue;
            }

            List<String> diffFields = new ArrayList<>();
            if (!Objects.equals(r.getName(), db.getName())) diffFields.add("name");
            if (!Objects.equals(r.getDetails(), db.getDetails())) diffFields.add("details");
            if (!Objects.equals(r.getImage(), db.getImage())) diffFields.add("image");
            if (!Objects.equals(r.getStock(), db.getStock())) diffFields.add("stock");
            if (!bigDecimalEq(r.getPrice(), db.getPrice())) diffFields.add("price");

            if (diffFields.isEmpty()) {
                matched++;
            } else {
                mismatched++;
                details.add(MismatchDetail.builder()
                        .productId(id)
                        .type(diffFields.contains("stock") ? "STOCK_MISMATCH"
                                : diffFields.contains("price") ? "PRICE_MISMATCH"
                                : "FIELD_MISMATCH")
                        .fields(diffFields)
                        .redisValue(r.toString())
                        .dbValue(db.toString())
                        .build());
            }
        }

        // Note: products present in Redis but NOT in DB are not visible from a paged DB scan.
        // To detect those, run a scheduled job that scans Redis keys `product:*` in batches
        // and checks existence via `productRepository.existsById`. Left as an extension point.

        return ConsistencyReport.builder()
                .page(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalChecked(page.getNumberOfElements())
                .matched(matched)
                .mismatched(mismatched)
                .missingInRedis(missingInRedis)
                .missingInDatabase(missingInDb)
                .details(details)
                .build();
    }

    private boolean bigDecimalEq(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }
}
