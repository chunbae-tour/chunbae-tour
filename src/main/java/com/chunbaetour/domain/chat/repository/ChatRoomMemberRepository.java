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

    // CHAT_003 중복 참여 체크 — ACTIVE 상태만 확인 (LEFT 재참여 허용)
    boolean existsByChatRoomIdAndUserIdAndMemberStateIn(Long chatRoomId, Long userId, List<ChatMemberState> activeStates);

    // CHAT_010 강퇴 재참여 차단 체크 — KICKED 상태만 확인
    boolean existsByChatRoomIdAndUserIdAndMemberState(Long chatRoomId, Long userId, ChatMemberState memberState);
}
