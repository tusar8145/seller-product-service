package com.enterprise.product.modules.product.consistency;

import com.enterprise.product.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/cache")
@RequiredArgsConstructor
public class ConsistencyCheckController {

    private final RedisDbConsistencyService consistencyService;

    /**
     * Compares products between Redis and DB page-by-page (batched) to avoid
     * loading the entire catalog into memory.
     *
     * @param page zero-based page index
     * @param size page size (bounded internally)
     */
    @GetMapping("/consistency")
    public ResponseEntity<ApiResponse<ConsistencyReport>> check(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size) {

        int bounded = Math.min(Math.max(size, 1), 2000);
        ConsistencyReport report = consistencyService.compare(PageRequest.of(page, bounded));
        return ResponseEntity.ok(ApiResponse.ok(report, "Consistency report for page " + page));
    }
}
