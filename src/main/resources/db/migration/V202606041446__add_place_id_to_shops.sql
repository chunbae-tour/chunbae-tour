-- Shop-Place 연결 구조 추가 (KAN-217)
-- place_id: 소속 장소. DB는 NULL 허용, 앱 레벨(@NotNull + @Valid)에서 필수 강제.
-- NOT NULL 미적용: 환경별 기존 데이터 충돌 방지.

ALTER TABLE shops ADD COLUMN place_id BIGINT NULL;

ALTER TABLE shops ADD INDEX idx_shops_place_id (place_id);
