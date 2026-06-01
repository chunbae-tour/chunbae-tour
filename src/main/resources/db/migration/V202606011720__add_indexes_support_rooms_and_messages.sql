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
