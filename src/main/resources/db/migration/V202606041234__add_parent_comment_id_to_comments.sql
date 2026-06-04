-- KAN-210: comments 테이블 계층형 댓글 구조를 위한 parent_comment_id 추가
ALTER TABLE comments
    ADD COLUMN parent_comment_id BIGINT NULL AFTER post_id,
    ADD INDEX idx_comment_parent (parent_comment_id, status, id);

CREATE INDEX idx_comment_post ON comments (post_id, post_type, parent_comment_id, id);
