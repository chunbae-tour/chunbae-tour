-- V202606021600의 ALGORITHM = INSTANT가 MySQL 8.4 환경에서 지원되지 않는 경우 대응.
-- 이미 공유 DB에 적용된 migration 파일(V202606021600)은 수정할 수 없으므로,
-- 인덱스가 존재하면 DROP 후 재생성(ALGORITHM 절 없이)하여 MySQL이 최적 알고리즘을 자동 선택하게 함.
--
-- 실행 조건:
--   - V202606021600이 INSTANT 실패로 인덱스를 생성하지 못한 DB: DROP 스킵, 재생성 성공
--   - V202606021600이 정상 적용된 DB: DROP 후 재생성 (무중단 — 동일 컬럼/이름)
--
-- MySQL 8.x는 DROP INDEX IF EXISTS 문법을 지원하지 않으므로
-- information_schema로 존재 여부를 확인 후 PREPARE/EXECUTE로 조건부 실행한다.

SET @index_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name   = 'refunds'
      AND index_name   = 'idx_refunds_status_created_at'
);

SET @drop_sql := IF(
    @index_exists > 0,
    'ALTER TABLE refunds DROP INDEX idx_refunds_status_created_at',
    'SELECT 1'
);

PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE refunds
    ADD INDEX idx_refunds_status_created_at (status, created_at);
