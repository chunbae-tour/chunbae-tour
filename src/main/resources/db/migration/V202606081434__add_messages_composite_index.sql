-- ============================
-- messages (chat_room_id, id) 복합 인덱스 추가 — 메시지 조회 (WHERE chat_room_id + id < cursor ORDER BY id DESC) filesort 방지
-- ASC 컴포지트 인덱스: InnoDB가 backward index scan으로 ORDER BY id DESC 처리 (idx_support_messages_room_id_id와 동일 패턴)
-- ============================
CREATE INDEX `idx_messages_room_id_id`
    ON `messages` (`chat_room_id`, `id`);
