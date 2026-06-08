-- 경도 컬럼 정밀도 확장: DECIMAL(10,7) → DECIMAL(11,7)
-- DECIMAL(10,7)은 정수부 3자리(최대 999)라 180은 저장 가능하나, 경도 최대값 180 보장을 위한 여유분 1자리 추가 (KAN-220 리뷰 반영)
ALTER TABLE traditional_markets
    MODIFY COLUMN lng DECIMAL(11, 7) NOT NULL;
