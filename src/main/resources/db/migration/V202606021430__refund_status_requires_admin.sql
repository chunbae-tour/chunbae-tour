-- REQUIRES_ADMIN을 리스트 끝에 추가 → MySQL 8.0.29+ INSTANT 알고리즘 적용 가능 (테이블 리빌드 없음)
ALTER TABLE refunds
    MODIFY COLUMN status ENUM(
        'APPROVED',
        'CANCELLED',
        'FAILED',
        'PENDING',
        'REJECTED',
        'REQUIRES_ADMIN'
    ) NOT NULL,
    ALGORITHM = INSTANT;
