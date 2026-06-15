-- 동행·자유 게시글 목록 조회 복합 인덱스 (고도화 PR 3 / 전략 2-2)
-- 공통 access pattern: WHERE status=? [AND region/meeting_date=?] AND id < :cursor ORDER BY id DESC
-- InnoDB 보조 인덱스는 리프 노드에 PK(id)를 자동 포함해 (col..., id)로 정렬·저장된다.
-- 따라서 말단에 id를 명시하지 않아도 cursor(id<?) 범위탐색 + id DESC 정렬이 충족된다(중복 선언 제거).

-- 동행: 기본 목록 (필터 없음)
CREATE INDEX idx_companion_status
    ON companion_posts (status);

-- 동행: region 단일 필터
CREATE INDEX idx_companion_status_region
    ON companion_posts (status, region);

-- 동행: meetingDate 단일 필터
CREATE INDEX idx_companion_status_meeting_date
    ON companion_posts (status, meeting_date);

-- 자유: 목록 조회 (필터 없음, 단일 패턴)
CREATE INDEX idx_free_status
    ON free_posts (status);
