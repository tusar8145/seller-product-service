CREATE TABLE IF NOT EXISTS product (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255)  NOT NULL,
    details      TEXT,
    image        VARCHAR(1000),
    stock        BIGINT        NOT NULL DEFAULT 0 CHECK (stock >= 0),
    price        NUMERIC(19,4) NOT NULL CHECK (price > 0),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version      BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_product_name  ON product (name);
CREATE INDEX IF NOT EXISTS idx_product_price ON product (price);
