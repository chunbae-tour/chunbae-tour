-- Shop-Place 연결 구조 추가 (KAN-217)
-- 통합검색 시 Place 소속 가게/메뉴 조회를 위한 FK 컬럼 추가.
-- nullable: 기존 가게는 연결 없음, 관리자가 수동 지정 또는 승인 시 선택적 지정.
ALTER TABLE shops
    ADD COLUMN place_id BIGINT NULL,
    ADD INDEX idx_shops_place_id (place_id),
    ADD CONSTRAINT fk_shops_place FOREIGN KEY (place_id) REFERENCES places (id);
