-- 댓글 행정 숨김(HIDDEN) 상태 도입 — 신고 DELETE 액션·자동숨김 임계·PERMANENT 벌크 숨김이
-- comment.status = HIDDEN으로 처리하므로 enum에 HIDDEN 추가.
-- companion_posts/free_posts/place_reviews는 baseline에 이미 HIDDEN 보유, comments만 누락이었음.
-- HIDDEN을 enum 맨 뒤에 추가 → 기존 값 순서 불변 + ALGORITHM=INPLACE 가능(무중단).
ALTER TABLE comments
    MODIFY COLUMN `status` enum('ACTIVE','DELETED','HIDDEN') NOT NULL,
    ALGORITHM=INPLACE, LOCK=NONE;
