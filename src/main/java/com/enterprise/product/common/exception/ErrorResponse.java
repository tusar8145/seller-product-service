package com.enterprise.product.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ErrorResponse {
    private boolean success;
    private String message;
    private int status;
    private String path;
    private OffsetDateTime timestamp;
    private Map<String, String> errors;
}
