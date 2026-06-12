-- KAN-294: 방장 위임 시 신규 방장에게 알림 발송.
-- notifications.type ENUM에 CHAT_OWNER_TRANSFERRED 추가.
ALTER TABLE notifications
    MODIFY COLUMN type ENUM(
        'CHAT_JOIN_APPROVED',
        'CHAT_JOIN_REJECTED',
        'CHAT_JOIN_REQUEST',
        'CHAT_MEMBER_KICKED',
        'CHAT_MESSAGE',
        'CHAT_OWNER_TRANSFERRED',
        'SUPPORT_MESSAGE',
        'SUPPORT_ROOM_CLOSED'
    ) NOT NULL;
