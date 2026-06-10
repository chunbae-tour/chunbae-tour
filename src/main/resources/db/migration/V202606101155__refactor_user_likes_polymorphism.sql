-- 기존 제약조건 드랍
ALTER TABLE user_likes DROP INDEX uk_user_likes_user_place;
ALTER TABLE user_likes DROP INDEX idx_user_likes_place;

-- 다형성 필드 추가
ALTER TABLE user_likes 
    ADD COLUMN target_type VARCHAR(50) DEFAULT 'PLACE' NOT NULL AFTER user_id,
    CHANGE COLUMN place_id target_id BIGINT NOT NULL;

-- 새 제약조건 추가
ALTER TABLE user_likes ADD CONSTRAINT uk_user_likes_type_target UNIQUE (user_id, target_type, target_id);
ALTER TABLE user_likes ADD INDEX idx_user_likes_target (target_type, target_id);
