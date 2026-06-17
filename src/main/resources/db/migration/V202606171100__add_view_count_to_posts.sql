-- 게시글 조회수 컬럼 추가 (KAN-165) — 자유/동행 게시판 공통
-- 댓글수(commentCount)는 별도 컬럼 없이 조회 시 comments 테이블 집계로 산출하므로 컬럼 추가 없음.
ALTER TABLE free_posts
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE companion_posts
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
