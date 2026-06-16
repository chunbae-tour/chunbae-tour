-- ============================
-- chat_room_members — 내 채팅방 목록 cursor 조회(findMyRoomsWithCursor) 최적화
-- 기존 UNIQUE(chat_room_id, user_id)는 user_id 선두가 아니라 이 쿼리에 활용 불가
-- ============================
ALTER TABLE `chat_room_members`
  ADD INDEX `idx_chat_room_members_user_id_member_state` (`user_id`, `member_state`);
