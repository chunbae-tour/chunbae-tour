-- findByStatusOrderByCreatedAt(PENDING) 쿼리 커버 인덱스
-- idx_refunds_retry(status, next_retry_at)는 FAILED 재시도 조회용이며 PENDING+created_at 정렬을 커버하지 못함
ALTER TABLE refunds
    ADD INDEX idx_refunds_status_created_at (status, created_at),
    ALGORITHM = INSTANT;
