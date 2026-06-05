-- findByStatusOrderByCreatedAt(PENDING) 쿼리 커버 인덱스
-- idx_refunds_retry(status, next_retry_at)는 FAILED 재시도 조회용이며 PENDING+created_at 정렬을 커버하지 못함
-- 주의: 본래 `ALGORITHM = INSTANT`였으나 MySQL 8.4는 secondary index 추가에 INSTANT를 지원하지 않아
-- (ErrorCode 1845, SQLSTATE 0A000) fresh-DB 부팅(신규 prod/CI testcontainers)이 이 지점에서 정지했다.
-- 운영 prod 미배포 시점이라 ALGORITHM 절 제거(인덱스 생성은 유지) — MySQL이 최적 알고리즘 자동 선택.
-- 기존 dev DB는 본 파일 checksum 변경으로 validate 실패 가능 → flyway repair 1회 필요. (KAN-212 S2)
ALTER TABLE refunds
    ADD INDEX idx_refunds_status_created_at (status, created_at);
