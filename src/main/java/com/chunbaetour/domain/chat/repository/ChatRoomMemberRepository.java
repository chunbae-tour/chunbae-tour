package com.chunbaetour.domain.chat.repository;

import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    // 특정 방 내 특정 유저의 멤버 레코드 조회 — 상태 확인·변경 공통 진입점
    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    // 방 전체 멤버 목록 (상태 무관) — 강퇴·목록 조회 시 사용
    List<ChatRoomMember> findByChatRoomId(Long chatRoomId);

    // 내가 현재 활동 중인 방 단순 조회 — chatRoom fetch join 없음, 목록 조회엔 findMyRoomsWithCursor 사용
    List<ChatRoomMember> findByUserIdAndMemberStateIn(Long userId, List<ChatMemberState> states);

    // 내 채팅방 목록 cursor 페이징 — chatRoom fetch join N+1 방지, chatRoomId DESC
    @Query("SELECT m FROM ChatRoomMember m JOIN FETCH m.chatRoom c " +
           "WHERE m.userId = :userId AND m.memberState IN :states AND c.id < :cursorId " +
           "ORDER BY c.id DESC")
    List<ChatRoomMember> findMyRoomsWithCursor(
            @Param("userId") Long userId,
            @Param("states") List<ChatMemberState> states,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // CHAT_003 중복 참여 체크 — ACTIVE 상태만 확인 (LEFT 재참여 허용)
    boolean existsByChatRoomIdAndUserIdAndMemberStateIn(Long chatRoomId, Long userId, List<ChatMemberState> activeStates);

    // CHAT_010 강퇴 재참여 차단 체크 — KICKED 상태만 확인
    boolean existsByChatRoomIdAndUserIdAndMemberState(Long chatRoomId, Long userId, ChatMemberState memberState);

    // 채팅방 상세 조회 — 현재 활동 중인 멤버만, 참여 순서(joinedAt ASC, id ASC) 정렬
    List<ChatRoomMember> findByChatRoomIdAndMemberStateInOrderByJoinedAtAscIdAsc(Long chatRoomId, List<ChatMemberState> states);
}
