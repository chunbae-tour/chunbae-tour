-- KAN-253: 상인 QR 재발급(분실·도용 대응). 가게별 회전 가능한 nonce를 QR payload에 포함해 옛 QR 무효화.
-- 1) nullable로 컬럼 추가 → 2) 기존 가게에 UUID nonce 채움 → 3) NOT NULL 제약.
ALTER TABLE shops ADD COLUMN qr_nonce VARCHAR(36) NULL;
UPDATE shops SET qr_nonce = UUID() WHERE qr_nonce IS NULL;
ALTER TABLE shops MODIFY COLUMN qr_nonce VARCHAR(36) NOT NULL;
