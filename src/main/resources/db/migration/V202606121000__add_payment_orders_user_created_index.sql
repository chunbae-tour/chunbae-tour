-- 일일 충전 한도 검증(KAN-293)용 인덱스.
-- DailyChargeLimiter가 charge 요청마다 사용자별 당일 충전 누적액을 SUM 조회한다:
--   WHERE user_id = ? AND created_at >= ? AND created_at < ? AND (status = 'PENDING' OR pg_transaction_id IS NOT NULL)
-- (user_id, created_at) 복합 인덱스로 사용자의 당일 행만 좁힌 뒤 상태/pg_transaction_id는 행 내에서 필터한다.
-- user_id 단독 인덱스조차 없던 payment_orders의 결제 내역 조회(user_id 기준)도 함께 개선된다.
CREATE INDEX idx_payment_orders_user_created ON payment_orders (user_id, created_at);
