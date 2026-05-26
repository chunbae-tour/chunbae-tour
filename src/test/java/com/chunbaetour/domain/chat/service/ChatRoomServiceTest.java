package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.response.ChatRoomDetailResponse;
import com.chunbaetour.domain.chat.dto.response.ChatRoomMemberResponse;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private ChatRoomService chatRoomService;

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 100L;

    // ===== getRoomDetail =====

    @Test
    void getRoomDetail_owner_active_returns_detail() {
        // OWNER_ACTIVE 멤버는 상세 조회 가능 — 방장 권한 보유자
        ChatRoom room = stubRoom();
        ChatRoomMember ownerMember = stubMember(USER_ID, ChatMemberState.OWNER_ACTIVE);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.findByChatRoomIdAndMemberStateInOrderByJoinedAtAscIdAsc(eq(ROOM_ID), any()))
                .willReturn(List.of(ownerMember));

        ChatRoomDetailResponse response = chatRoomService.getRoomDetail(USER_ID, ROOM_ID);

        assertThat(response.chatRoomId()).isEqualTo(ROOM_ID);
        assertThat(response.members()).hasSize(1);
    }

    @Test
    void getRoomDetail_member_active_returns_detail() {
        // MEMBER_ACTIVE 멤버는 상세 조회 가능 — 방장 외 일반 참여자
        ChatRoom room = stubRoom();
        ChatRoomMember member = stubMember(USER_ID, ChatMemberState.MEMBER_ACTIVE);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.findByChatRoomIdAndMemberStateInOrderByJoinedAtAscIdAsc(eq(ROOM_ID), any()))
                .willReturn(List.of(member));

        ChatRoomDetailResponse response = chatRoomService.getRoomDetail(USER_ID, ROOM_ID);

        assertThat(response.members()).hasSize(1);
    }

    @Test
    void getRoomDetail_non_member_throws_CHAT_NOT_JOINED() {
        // 채팅방에 참여한 이력이 없는 사용자 — activeMembers 목록에 존재하지 않음
        // isMember 검사에서 예외가 발생하므로 ChatRoom 상세 필드 stubbing 불필요
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(mock(ChatRoom.class)));
        given(chatRoomMemberRepository.findByChatRoomIdAndMemberStateInOrderByJoinedAtAscIdAsc(eq(ROOM_ID), any()))
                .willReturn(List.of());

        assertThatThrownBy(() -> chatRoomService.getRoomDetail(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_NOT_JOINED);
    }

    @Test
    void getRoomDetail_kicked_member_throws_CHAT_NOT_JOINED() {
        // KICKED 상태는 ACTIVE_STATES 필터에 포함되지 않아 repository 조회 결과에서 제외됨
        // → 요청자가 목록에 없으므로 isMember = false → CHAT_NOT_JOINED
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(mock(ChatRoom.class)));
        given(chatRoomMemberRepository.findByChatRoomIdAndMemberStateInOrderByJoinedAtAscIdAsc(eq(ROOM_ID), any()))
                .willReturn(List.of());

        assertThatThrownBy(() -> chatRoomService.getRoomDetail(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_NOT_JOINED);
    }

    @Test
    void getRoomDetail_left_member_throws_CHAT_NOT_JOINED() {
        // LEFT 상태도 ACTIVE_STATES 필터에 포함되지 않아 repository 조회 결과에서 제외됨
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(mock(ChatRoom.class)));
        given(chatRoomMemberRepository.findByChatRoomIdAndMemberStateInOrderByJoinedAtAscIdAsc(eq(ROOM_ID), any()))
                .willReturn(List.of());

        assertThatThrownBy(() -> chatRoomService.getRoomDetail(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_NOT_JOINED);
    }

    @Test
    void getRoomDetail_room_not_found_throws_CHAT_ROOM_NOT_FOUND() {
        // 존재하지 않는 채팅방 조회 — findById empty 시 CHAT_ROOM_NOT_FOUND
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.getRoomDetail(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    // ===== getMembers =====

    @Test
    void getMembers_success_returns_member_list_with_account_info() {
        // 정상 조회 — ACTIVE 멤버 반환, Account 정보 포함 확인
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        LocalDateTime joinedAt = LocalDateTime.of(2025, 1, 1, 0, 0);
        ChatRoomMember member = stubMember(USER_ID, ChatMemberState.OWNER_ACTIVE);
        given(member.getJoinedAt()).willReturn(joinedAt);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        given(chatRoomMemberRepository.findByChatRoomIdAndMemberStateInOrderByJoinedAtAscIdAsc(eq(ROOM_ID), any()))
                .willReturn(List.of(member));
        Account account = mock(Account.class);
        given(account.getId()).willReturn(USER_ID);
        given(account.getNickname()).willReturn("여행자");
        given(account.getProfileImageUrl()).willReturn("https://cdn.example.com/img.jpg");
        given(account.getCompanionScore()).willReturn(4.5f);
        given(accountRepository.findAllById(List.of(USER_ID))).willReturn(List.of(account));

        List<ChatRoomMemberResponse> result = chatRoomService.getMembers(USER_ID, ROOM_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(USER_ID);
        assertThat(result.get(0).nickname()).isEqualTo("여행자");
        assertThat(result.get(0).profileImageUrl()).isEqualTo("https://cdn.example.com/img.jpg");
        assertThat(result.get(0).companionScore()).isEqualTo(4.5f);
        assertThat(result.get(0).memberState()).isEqualTo(ChatMemberState.OWNER_ACTIVE);
        assertThat(result.get(0).joinedAt()).isEqualTo(joinedAt);
    }

    @Test
    void getMembers_roomNotFound_throws_CHAT_ROOM_NOT_FOUND() {
        // 존재하지 않는 채팅방 — CHAT_ROOM_NOT_FOUND
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(false);

        assertThatThrownBy(() -> chatRoomService.getMembers(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void getMembers_notMember_throws_CHAT_NOT_JOINED() {
        // 비참여자 접근 — findByChatRoomIdAndUserId 결과 없으면 CHAT_NOT_JOINED
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.getMembers(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_NOT_JOINED);
    }

    @Test
    void getMembers_deletedAccount_returnsFallback() {
        // 탈퇴 계정(Account 삭제)은 USER_NOT_FOUND 대신 "탈퇴한 사용자" fallback으로 반환
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        ChatRoomMember member1 = mock(ChatRoomMember.class);
        given(member1.getUserId()).willReturn(USER_ID);
        given(member1.getMemberState()).willReturn(ChatMemberState.OWNER_ACTIVE);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member1));
        ChatRoomMember member2 = mock(ChatRoomMember.class);
        given(member2.getUserId()).willReturn(2L);
        given(member2.getMemberState()).willReturn(ChatMemberState.MEMBER_ACTIVE);
        given(member2.getJoinedAt()).willReturn(LocalDateTime.of(2025, 1, 2, 0, 0));
        given(chatRoomMemberRepository.findByChatRoomIdAndMemberStateInOrderByJoinedAtAscIdAsc(eq(ROOM_ID), any()))
                .willReturn(List.of(member1, member2));
        Account account = mock(Account.class);
        given(account.getId()).willReturn(USER_ID);
        given(account.getNickname()).willReturn("여행자");
        given(account.getProfileImageUrl()).willReturn("https://cdn.example.com/img.jpg");
        given(account.getCompanionScore()).willReturn(4.5f);
        // member2의 Account 누락 — findAllById가 1건만 반환 (탈퇴 계정 시나리오)
        given(accountRepository.findAllById(any())).willReturn(List.of(account));

        List<ChatRoomMemberResponse> result = chatRoomService.getMembers(USER_ID, ROOM_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(1).userId()).isEqualTo(2L);
        assertThat(result.get(1).nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(result.get(1).profileImageUrl()).isNull();
        assertThat(result.get(1).companionScore()).isEqualTo(0f);
    }

    // ===== decodeCursor — getMyRooms를 통해 간접 검증 =====
    // cursor가 유효하지 않으면 repository 호출 전에 예외가 발생함

    @Test
    void getMyRooms_negative_cursor_throws_INVALID_CURSOR() {
        // IDENTITY PK는 1 이상 — 음수 id는 조작된 커서로 판단
        String cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("-1".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> chatRoomService.getMyRooms(USER_ID, cursor, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    @Test
    void getMyRooms_zero_cursor_throws_INVALID_CURSOR() {
        // id=0도 IDENTITY PK 범위 위반 — 1 이상만 허용
        String cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("0".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> chatRoomService.getMyRooms(USER_ID, cursor, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    @Test
    void getMyRooms_malformed_base64_cursor_throws_INVALID_CURSOR() {
        // Base64 URL-safe 알파벳(A-Z, a-z, 0-9, -, _) 외 문자 → 디코딩 자체 실패
        String cursor = "!!!invalid!!!";

        assertThatThrownBy(() -> chatRoomService.getMyRooms(USER_ID, cursor, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    // ===== helpers =====

    private ErrorCode extractErrorCode(Throwable ex) {
        return ((BusinessException) ex).getErrorCode();
    }

    private ChatRoom stubRoom() {
        ChatRoom room = mock(ChatRoom.class);
        given(room.getId()).willReturn(ROOM_ID);
        given(room.getPostId()).willReturn(10L);
        given(room.getTitle()).willReturn("테스트 채팅방");
        given(room.getDescription()).willReturn("설명");
        given(room.getOwnerId()).willReturn(USER_ID);
        given(room.getMaxMembers()).willReturn(5);
        given(room.getStatus()).willReturn(ChatRoomStatus.OPEN);
        return room;
    }

    private ChatRoomMember stubMember(Long userId, ChatMemberState state) {
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(member.getUserId()).willReturn(userId);
        given(member.getMemberState()).willReturn(state);
        return member;
    }
}
