package com.enterprise.product.modules.stock.dto;

import com.enterprise.product.modules.stock.model.StockUpdateType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockUpdateResponse {
    private Long productId;
    private StockUpdateType updateType;
    private Long quantity;
    private Long stockBefore;
    private Long stockAfter;
    private String referenceId;
    private boolean applied;
    private String message;
}
