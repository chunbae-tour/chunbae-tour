-- Shop-Place 연결 구조 추가 (KAN-217)
-- 모든 Shop은 반드시 하나의 Place에 속해야 함 (NOT NULL 정책).
-- 기존 데이터 채우기는 각 환경의 seed/dummy 스크립트에서 처리.
-- FK 미선언: 통합 테스트 환경에서 places 데이터 없이도 동작 가능하도록.

-- 1) NULL 허용으로 컬럼 추가
ALTER TABLE shops ADD COLUMN place_id BIGINT NULL;

-- 2) 기존 rows가 없는 신규 DB는 바로 NOT NULL 전환 가능.
--    기존 rows가 있는 환경은 seed/dummy 스크립트가 먼저 place_id를 채운 뒤 실행되어야 함.
ALTER TABLE shops MODIFY COLUMN place_id BIGINT NOT NULL;

-- 3) 인덱스 추가
ALTER TABLE shops ADD INDEX idx_shops_place_id (place_id);
