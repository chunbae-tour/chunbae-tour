-- V202606021600의 ALGORITHM = INSTANT가 MySQL 8.4 환경에서 지원되지 않는 경우 대응.
-- 이미 공유 DB에 적용된 migration 파일(V202606021600)은 수정할 수 없으므로,
-- 인덱스가 존재하면 DROP 후 재생성(ALGORITHM 절 없이)하여 MySQL이 최적 알고리즘을 자동 선택하게 함.
--
-- 실행 조건:
--   - V202606021600이 INSTANT 실패로 인덱스를 생성하지 못한 DB: DROP은 에러(무시), 재생성 성공
--   - V202606021600이 정상 적용된 DB: DROP 후 재생성 (무중단 — 동일 컬럼/이름)
ALTER TABLE refunds
    DROP INDEX IF EXISTS idx_refunds_status_created_at;

ALTER TABLE refunds
    ADD INDEX idx_refunds_status_created_at (status, created_at);
