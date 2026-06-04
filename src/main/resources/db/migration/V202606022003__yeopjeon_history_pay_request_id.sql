-- QR 결제 이력에서 원본 결제 요청 역추적을 위한 컬럼 추가 (KAN-206)
-- ADD COLUMN NULL은 MySQL 8.0+에서 INSTANT 지원 (테이블 리빌드 없음). 프로젝트 MySQL 8.4 사용.
ALTER TABLE yeopjeon_histories
    ADD COLUMN pay_request_id VARCHAR(36) NULL,
    ALGORITHM = INSTANT;
