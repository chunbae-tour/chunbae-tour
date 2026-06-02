-- ============================
-- support_messages (support_room_id, id) 복합 인덱스 추가 — cursor 페이징 (id < :cursorId ORDER BY id DESC) 성능 보장
-- ============================
CREATE INDEX `idx_support_messages_room_id_id`
    ON `support_messages` (`support_room_id`, `id`);
