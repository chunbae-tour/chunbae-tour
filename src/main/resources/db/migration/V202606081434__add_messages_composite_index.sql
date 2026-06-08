-- ============================
-- messages (chat_room_id, id DESC) 복합 인덱스 추가 — 메시지 조회 (WHERE chat_room_id + ORDER BY id DESC) filesort 방지
-- ============================
ALTER TABLE `messages`
    ADD INDEX `idx_messages_room_id_id_desc` (`chat_room_id`, `id` DESC);
