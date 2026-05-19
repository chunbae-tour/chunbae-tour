package com.chunbaetour.domain.chat.entity;

import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "current_members", nullable = false)
    private int currentMembers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 생성자 진입 시점에 도메인 불변식을 강제. 서비스 레이어 검증과 이중 방어.
    @Builder
    private ChatRoom(Long postId, Long ownerId, String title, String description, int maxMembers) {
        if (maxMembers < 2 || maxMembers > 50) {
            throw new BusinessException(ErrorCode.INVALID_CHAT_CAPACITY);
        }
        this.postId = postId;
        this.ownerId = ownerId;
        this.title = title;
        this.description = description;
        this.maxMembers = maxMembers;
        this.currentMembers = 1; // 개설자가 첫 번째 멤버
        this.status = ChatRoomStatus.OPEN;
    }

    // 개설자가 채팅방을 영구 종료. CLOSED 이후 상태 전환 없음.
    public void close() {
        this.status = ChatRoomStatus.CLOSED;
    }

    // 서비스에서 직접 FULL 전환이 필요한 경우 사용 (e.g. 동시성 보정).
    // CLOSED 방 전이 차단 — 종료 불변식 보호.
    // currentMembers < maxMembers면 실제 정원 미달이므로 FULL 전환 불가.
    public void markFull() {
        if (this.status == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (this.currentMembers < this.maxMembers) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.status = ChatRoomStatus.FULL;
    }

    // FULL → OPEN 전환 전용 (누군가 퇴장해 자리가 생긴 경우).
    // CLOSED 방은 재오픈 불가 — close()는 되돌릴 수 없는 종료 처리.
    public void markOpen() {
        if (this.status == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.status = ChatRoomStatus.OPEN;
    }

    // 참여 확정 시점에 호출. CLOSED/FULL 방 진입 차단은 서비스와 이중 방어.
    public void incrementMembers() {
        if (this.status == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (this.currentMembers >= this.maxMembers) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_FULL);
        }
        this.currentMembers++;
        if (this.currentMembers >= this.maxMembers) {
            this.status = ChatRoomStatus.FULL;
        }
    }

    // 멤버 퇴장/강퇴 시 호출. FULL이었다면 자리가 생겼으므로 OPEN 복귀.
    // CLOSED 방은 이미 종료 상태이므로 OPEN 복귀 대상에서 제외.
    public void decrementMembers() {
        if (this.currentMembers > 0) {
            this.currentMembers--;
        }
        if (this.status == ChatRoomStatus.FULL) {
            this.status = ChatRoomStatus.OPEN;
        }
    }
}
