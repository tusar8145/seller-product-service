package com.enterprise.product.modules.bulk;

/**
 * Identifies the column layout of an uploaded Excel file so the
 * streaming reader can map cells to the correct ExcelRow fields.
 *
 *  PRODUCT_CREATE -> Name | Details | Image | Stock | Price
 *  STOCK_UPDATE   -> Id   | Stock (signed delta)
 */
public enum ExcelSchema {
    PRODUCT_CREATE,
    STOCK_UPDATE
}