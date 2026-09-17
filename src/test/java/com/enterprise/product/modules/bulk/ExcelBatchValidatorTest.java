package com.enterprise.product.modules.bulk;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelBatchValidatorTest {

    private final ExcelBatchValidator validator = new ExcelBatchValidator();

    @Test
    void create_valid_row_noErrors() {
        ExcelRow row = ExcelRow.builder()
                .rowNumber(2)
                .name("Widget")
                .stock(10L)
                .price(new BigDecimal("9.99"))
                .build();

        assertThat(validator.validateRowForCreate(row)).isEmpty();
    }

    @Test
    void create_missingPrice_returnsError() {
        ExcelRow row = ExcelRow.builder()
                .rowNumber(2).name("Widget").stock(10L).price(null).build();

        assertThat(validator.validateRowForCreate(row))
                .containsExactly("Price is required");
    }

    @Test
    void stockUpdate_valid_row_noErrors() {
        ExcelRow row = ExcelRow.builder().rowNumber(2).id(100L).stock(-5L).build();
        assertThat(validator.validateRowForStockUpdate(row)).isEmpty();
    }

    @Test
    void stockUpdate_missingId_returnsError() {
        ExcelRow row = ExcelRow.builder().rowNumber(2).id(null).stock(5L).build();
        assertThat(validator.validateRowForStockUpdate(row))
                .contains("Product id is required");
    }
}