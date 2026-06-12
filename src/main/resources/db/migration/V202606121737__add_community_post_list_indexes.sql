-- 동행·자유 게시글 목록 조회 복합 인덱스 (고도화 PR 3 / 전략 2-2)
-- 공통 access pattern: WHERE status=? [AND region/meeting_date=?] AND id < :cursor ORDER BY id DESC
-- 말단 컬럼을 id로 둬 cursor 범위 탐색 + id DESC 정렬을 인덱스만으로 처리 (filesort 제거).

-- 동행: 기본 목록 (필터 없음)
CREATE INDEX idx_companion_status_id
    ON companion_posts (status, id);

-- 동행: region 단일 필터
CREATE INDEX idx_companion_status_region_id
    ON companion_posts (status, region, id);

-- 동행: meetingDate 단일 필터
CREATE INDEX idx_companion_status_meeting_date_id
    ON companion_posts (status, meeting_date, id);

-- 자유: 목록 조회 (필터 없음, 단일 패턴)
CREATE INDEX idx_free_status_id
    ON free_posts (status, id);
