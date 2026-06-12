-- 상인 홈 대시보드(KAN-283) 쿼리 + QR 결제 스케줄러용 인덱스.
-- qr_pay_requests는 PK + UNIQUE(pay_request_id, pending_key)뿐이라 아래 쿼리가 모두 풀스캔이었다.

-- 오늘/어제 매출 SUM·완료 목록·시간대 분포: WHERE shop_id IN (?) AND status = 'COMPLETED' AND completed_at ∈ [?, ?) ORDER BY completed_at DESC
CREATE INDEX idx_qr_pay_shop_status_completed ON qr_pay_requests (shop_id, status, completed_at);

-- 미완료(거절+만료) 카운트: WHERE shop_id IN (?) AND status IN ('REJECTED','EXPIRED') AND expired_at ∈ [?, ?)
-- 상인 대기 목록(findPendingByShopIds): WHERE shop_id IN (?) AND status = 'PENDING' AND expired_at > ? 도 함께 커버.
CREATE INDEX idx_qr_pay_shop_status_expired ON qr_pay_requests (shop_id, status, expired_at);

-- 만료 스케줄러(bulkExpireOverdue): WHERE status = 'PENDING' AND expired_at <= ? — shop_id 필터가 없어 별도 인덱스.
CREATE INDEX idx_qr_pay_status_expired ON qr_pay_requests (status, expired_at);
