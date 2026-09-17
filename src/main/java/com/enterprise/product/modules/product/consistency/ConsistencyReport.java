package com.enterprise.product.modules.product.consistency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConsistencyReport {
    private int page;
    private int pageSize;
    private long totalChecked;
    private long matched;
    private long mismatched;
    private long missingInRedis;
    private long missingInDatabase;
    @Builder.Default private List<MismatchDetail> details = new ArrayList<>();
}
