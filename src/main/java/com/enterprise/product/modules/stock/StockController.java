package com.enterprise.product.modules.stock;

import com.enterprise.product.common.dto.ApiResponse;
import com.enterprise.product.modules.stock.dto.StockUpdateRequest;
import com.enterprise.product.modules.stock.dto.StockUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/{productId}/stock")
@RequiredArgsConstructor
@Tag(name = "Stock API", description = "High-traffic stock updates with Redis reservation + async DB sync")
public class StockController {

    private final StockReservationService reservationService;
    private final StockUpdateHistoryRepository historyRepository;

    @PostMapping
    @Operation(summary = "Increment or decrement stock. Redis is the fast reservation layer; DB sync is async.")
    public ResponseEntity<ApiResponse<StockUpdateResponse>> update(
            @PathVariable Long productId,
            @Valid @RequestBody StockUpdateRequest request,
            @RequestHeader(value = "X-User", defaultValue = "anonymous") String user) {
        StockUpdateResponse response = reservationService.apply(productId, request, user);
        String msg = response.isApplied() ? "Stock updated" : "Idempotent replay ignored";
        return ResponseEntity.ok(ApiResponse.ok(response, msg));
    }

    @GetMapping("/history")
    @Operation(summary = "Paginated stock update history for a product")
    public ResponseEntity<ApiResponse<Page<?>>> history(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<?> result = historyRepository.findByProductId(
                productId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.ok(result, "Stock history"));
    }
}
