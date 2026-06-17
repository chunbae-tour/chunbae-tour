-- 누적 신고 카운트 쿼리(countBy reported_user_id + target_type + status + resolved_at) 복합 인덱스.
-- 기존 단일 idx_reports_reported_user_id로는 reported_user_id 접두만 타고 나머지 3컬럼은 행 스캔 →
-- 신고량 증가 시 풀스캔 + 동시 제재평가 락 경합. 4컬럼 복합으로 인덱스만으로 카운트.
-- ESR 순서: equality(reported_user_id, target_type, status) → range(resolved_at).
-- CREATE INDEX의 algorithm/lock 옵션은 공백 구분(콤마 X — 콤마는 ALTER TABLE 전용).
CREATE INDEX idx_reports_sanction_count
    ON reports (reported_user_id, target_type, status, resolved_at)
    ALGORITHM=INPLACE LOCK=NONE;

-- 단일 인덱스는 복합 인덱스의 leftmost-prefix(reported_user_id)로 흡수되어 중복 → 드롭(쓰기 비용 절감, FK 없음).
DROP INDEX idx_reports_reported_user_id ON reports;
