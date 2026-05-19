package com.chunbaetour.domain.chat.repository;

import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    // 특정 방 내 특정 유저의 멤버 레코드 조회 — 상태 확인·변경 공통 진입점
    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    // 방 전체 멤버 목록 (상태 무관) — 강퇴·목록 조회 시 사용
    List<ChatRoomMember> findByChatRoomId(Long chatRoomId);

    // 내가 현재 활동 중인 방 목록 조회 — OWNER_ACTIVE·MEMBER_ACTIVE 필터링하여 호출
    List<ChatRoomMember> findByUserIdAndMemberStateIn(Long userId, List<ChatMemberState> states);

    // 이미 참여 이력이 있는지 확인 (KICKED 포함) — CHAT_003·CHAT_010 선행 체크용
    boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);
}
