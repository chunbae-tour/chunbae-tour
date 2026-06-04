-- Shop-Place 연결 구조 추가 (KAN-217)
-- place_id: 소속 장소. 선택값 — null이면 장소 미연결 가게 허용.
-- FK는 V202606041500에서 ON DELETE SET NULL 옵션과 함께 추가.

ALTER TABLE shops ADD COLUMN place_id BIGINT NULL;

ALTER TABLE shops ADD INDEX idx_shops_place_id (place_id);
