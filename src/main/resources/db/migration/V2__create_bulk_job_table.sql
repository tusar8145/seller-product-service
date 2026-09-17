CREATE TABLE IF NOT EXISTS bulk_job (
    id              UUID PRIMARY KEY,
    job_type        VARCHAR(30)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    input_s3_key    VARCHAR(500),
    output_s3_key   VARCHAR(500),
    error_s3_key    VARCHAR(500),
    total_rows      BIGINT       NOT NULL DEFAULT 0,
    processed_rows  BIGINT       NOT NULL DEFAULT 0,
    success_count   BIGINT       NOT NULL DEFAULT 0,
    failure_count   BIGINT       NOT NULL DEFAULT 0,
    error_message   TEXT,
    submitted_by    VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_bulk_job_status  ON bulk_job (status);
CREATE INDEX IF NOT EXISTS idx_bulk_job_type    ON bulk_job (job_type);
CREATE INDEX IF NOT EXISTS idx_bulk_job_created ON bulk_job (created_at);
