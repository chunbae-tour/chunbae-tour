-- QR 결제 이력에서 원본 결제 요청 역추적을 위한 컬럼 추가 (KAN-206)
ALTER TABLE yeopjeon_histories
    ADD COLUMN pay_request_id VARCHAR(36) NULL,
    ALGORITHM = INSTANT;
