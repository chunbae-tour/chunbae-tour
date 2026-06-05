-- AES-128 암호화 적용으로 account_number 컬럼 길이 30 → 255 확장
-- Base64 인코딩된 암호문은 평문보다 길어 기존 30자 제한으로 저장 불가
-- 기존 평문 데이터가 존재하는 경우 별도 암호화 마이그레이션 스크립트 실행 필요
ALTER TABLE shop_wallets
    MODIFY COLUMN account_number VARCHAR(255);
