ALTER TABLE refunds
    MODIFY COLUMN status ENUM(
        'APPROVED',
        'CANCELLED',
        'FAILED',
        'PENDING',
        'REJECTED',
        'REQUIRES_ADMIN'
    ) NOT NULL;
