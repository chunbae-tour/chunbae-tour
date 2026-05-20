package com.chunbaetour.domain.chat.type;

public enum MessageType {
    TEXT,   // 일반 텍스트
    IMAGE,  // 이미지 — fileUrl 필수
    FILE,   // 파일 — fileUrl, fileName, fileSize 필수
    SYSTEM  // 입장·퇴장·강퇴 등 시스템 알림 — senderId 의미 없음
}
