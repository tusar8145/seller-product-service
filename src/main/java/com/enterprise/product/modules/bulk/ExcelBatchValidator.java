package com.enterprise.product.modules.bulk;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExcelBatchValidator {

    public List<String> validateRowForCreate(ExcelRow row) {
        List<String> errors = new ArrayList<>();
        if (row.getName() == null || row.getName().isBlank()) errors.add("Name is required");
        else if (row.getName().length() > 255) errors.add("Name exceeds 255 chars");

        if (row.getStock() == null) errors.add("Stock is required");
        else if (row.getStock() < 0) errors.add("Stock cannot be negative");

        if (row.getPrice() == null) errors.add("Price is required");
        else if (row.getPrice().compareTo(BigDecimal.ZERO) <= 0) errors.add("Price must be positive");
        return errors;
    }

    public List<String> validateRowForStockUpdate(ExcelRow row) {
        List<String> errors = new ArrayList<>();
        if (row.getId() == null) errors.add("Product id is required");
        if (row.getStock() == null) errors.add("Stock delta is required (may be negative)");
        return errors;
    }
}
