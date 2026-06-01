-- ============================
-- support_rooms status 컬럼 확장 — IN_PROGRESS(11자) 수용
-- ============================
ALTER TABLE `support_rooms`
    MODIFY COLUMN `status` varchar(15) NOT NULL DEFAULT 'WAITING';

-- ============================
-- support_rooms 인덱스
-- ============================
CREATE INDEX `idx_support_rooms_user_id_id`
    ON `support_rooms` (`user_id`, `id`);

CREATE INDEX `idx_support_rooms_status_id`
    ON `support_rooms` (`status`, `id`);

-- ============================
-- support_messages 인덱스
-- ============================
CREATE INDEX `idx_support_messages_room_sent_at`
    ON `support_messages` (`support_room_id`, `sent_at`);

-- ============================
-- 활성 상담방 1개 제한 — WAITING/IN_PROGRESS는 user당 1개만 허용
-- CLOSED는 NULL로 매핑되어 unique 제약에서 제외됨
-- ============================
ALTER TABLE `support_rooms`
    ADD COLUMN `active_user_id` bigint GENERATED ALWAYS AS (
        CASE WHEN `status` IN ('WAITING', 'IN_PROGRESS') THEN `user_id` ELSE NULL END
    ) STORED;

CREATE UNIQUE INDEX `uk_support_rooms_active_user`
    ON `support_rooms` (`active_user_id`);
