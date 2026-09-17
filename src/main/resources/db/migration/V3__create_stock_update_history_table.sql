CREATE TABLE IF NOT EXISTS stock_update_history (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT       NOT NULL,
    update_type   VARCHAR(20)  NOT NULL,
    delta         BIGINT       NOT NULL,
    stock_before  BIGINT       NOT NULL,
    stock_after   BIGINT       NOT NULL,
    reference_id  VARCHAR(100) NOT NULL,
    reason        VARCHAR(255),
    created_by    VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stock_history_reference UNIQUE (reference_id),
    CONSTRAINT fk_stock_history_product FOREIGN KEY (product_id) REFERENCES product (id)
);
CREATE INDEX IF NOT EXISTS idx_stock_history_product ON stock_update_history (product_id);
CREATE INDEX IF NOT EXISTS idx_stock_history_created ON stock_update_history (created_at);
