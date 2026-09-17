package com.enterprise.product.modules.stock.dto;

import com.enterprise.product.modules.stock.model.StockUpdateType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockUpdateRequest {

    @NotNull(message = "updateType is required (INCREMENT | DECREMENT)")
    private StockUpdateType updateType;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be >= 1")
    private Long quantity;

    /** Client-supplied idempotency key. Replays with same key are ignored. */
    @NotBlank(message = "referenceId is required for idempotency")
    private String referenceId;

    private String reason;
}
