package com.enterprise.product.modules.product.consistency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MismatchDetail {
    private Long productId;
    private String type; // MISSING_IN_REDIS | MISSING_IN_DB | FIELD_MISMATCH | STOCK_MISMATCH | PRICE_MISMATCH
    private List<String> fields;
    private String redisValue;
    private String dbValue;
}
