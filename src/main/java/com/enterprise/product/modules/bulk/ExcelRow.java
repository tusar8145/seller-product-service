package com.enterprise.product.modules.bulk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExcelRow {
    private int rowNumber;
    private Long id;              // optional for stock update rows
    private String name;
    private String details;
    private String image;
    private Long stock;
    private BigDecimal price;
}
