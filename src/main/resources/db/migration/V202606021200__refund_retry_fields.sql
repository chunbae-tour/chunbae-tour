ALTER TABLE refunds
    ADD COLUMN retry_count   INT          NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at DATETIME(6)  NULL,
    ADD INDEX idx_refunds_retry (status, next_retry_at);
