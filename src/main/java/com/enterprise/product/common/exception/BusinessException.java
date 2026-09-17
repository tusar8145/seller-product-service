package com.enterprise.product.common.exception;

/** Generic business-rule violation (insufficient stock, locked resource, etc.). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
