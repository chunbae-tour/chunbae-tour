-- KAN-298: 고아 PENDING 충전 주문 재조정 스케줄러의 stale 조회 인덱스.
-- findStalePendingOrders: WHERE status = 'PENDING' AND created_at < ? ORDER BY created_at ASC
-- 결제 테이블 증가 시 (status, created_at) 복합 인덱스가 없으면 풀스캔 + filesort 발생 →
-- status 등치 + created_at 범위/정렬을 한 인덱스로 커버해 회피한다.
CREATE INDEX idx_payment_orders_status_created ON payment_orders (status, created_at);
