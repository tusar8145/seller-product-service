package com.enterprise.product.modules.bulk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExcelFailure {
    private int rowNumber;
    private String identifier;
    private String errorMessage;
}
