-- 경도 컬럼 정수부 확장: DECIMAL(10,7) → DECIMAL(11,7)
-- DECIMAL(10,7)은 정수부 3자리 → 180.0000000 저장 불가 (KAN-220 리뷰 반영)
ALTER TABLE traditional_markets
    MODIFY COLUMN lng DECIMAL(11, 7) NOT NULL;
