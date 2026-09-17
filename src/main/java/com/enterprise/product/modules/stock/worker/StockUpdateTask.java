package com.enterprise.product.modules.stock.worker;

import com.enterprise.product.modules.stock.model.StockUpdateType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockUpdateTask {
    private String taskId;          // unique per attempt
    private Long productId;
    private StockUpdateType updateType;
    private Long delta;
    private Long stockBefore;       // Redis view at time of reservation
    private Long stockAfter;        // Redis view after reservation
    private String referenceId;     // idempotency key
    private String reason;
    private String createdBy;
}
