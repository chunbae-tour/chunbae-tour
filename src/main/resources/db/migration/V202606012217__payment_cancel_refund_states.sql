ALTER TABLE payment_orders
    MODIFY COLUMN status ENUM(
        'CANCELLED',
        'COMPLETED',
        'FAILED',
        'PENDING',
        'REFUNDED',
        'PARTIAL_CANCELLED',
        'ADJUSTMENT_REQUIRED'
    ) NOT NULL;

ALTER TABLE refunds
    MODIFY COLUMN status ENUM(
        'APPROVED',
        'CANCELLED',
        'FAILED',
        'PENDING',
        'REJECTED'
    ) NOT NULL;
