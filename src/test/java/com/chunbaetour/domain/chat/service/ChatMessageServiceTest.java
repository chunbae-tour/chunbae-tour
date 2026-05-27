package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.request.ChatSendMessageRequest;
import com.chunbaetour.domain.chat.dto.response.ChatMessageResponse;
import com.chunbaetour.domain.chat.entity.Message;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.repository.MessageRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.MessageType;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.ratelimit.RateLimitDecision;
import com.chunbaetour.domain.common.ratelimit.RateLimiter;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatRedisPubSubService chatRedisPubSubService;

    @Mock
    private RateLimiter rateLimiter;

    @InjectMocks
    private ChatMessageService chatMessageService;

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 100L;

    @Test
    void sendMessage_success_savesAndPublishes() {
        // ACTIVE 멤버 → rate limit 허용 → 저장 후 Redis 발행
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(29));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(ROOM_ID), eq(USER_ID), any())).willReturn(true);
        Account sender = mock(Account.class);
        given(sender.getNickname()).willReturn("테스터");
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(sender));
        Message saved = mock(Message.class);
        given(saved.getId()).willReturn(1L);
        given(saved.getChatRoomId()).willReturn(ROOM_ID);
        given(saved.getSenderId()).willReturn(USER_ID);
        given(saved.getMessageType()).willReturn(MessageType.TEXT);
        given(saved.getContent()).willReturn("안녕하세요");
        given(saved.getCreatedAt()).willReturn(LocalDateTime.of(2026, 5, 26, 12, 0));
        given(messageRepository.save(any())).willReturn(saved);

        chatMessageService.sendMessage(USER_ID, ROOM_ID, new ChatSendMessageRequest("안녕하세요"));

        then(messageRepository).should().save(any(Message.class));
        then(chatRedisPubSubService).should().publish(any(), any());
    }

    @Test
    void sendMessage_rateLimitExceeded_throws_TOO_MANY_REQUESTS() {
        // rate limit 초과 — 30회/10초 정책 위반 시 COMMON_006
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.denied(java.time.Duration.ofSeconds(5)));

        assertThatThrownBy(() ->
                chatMessageService.sendMessage(USER_ID, ROOM_ID, new ChatSendMessageRequest("hello")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void sendMessage_notMember_throws_CHAT_NOT_JOINED() {
        // 비참여자·강퇴·퇴장 모두 ACTIVE_STATES 미포함 → CHAT_NOT_JOINED
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(29));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(ROOM_ID), eq(USER_ID), any())).willReturn(false);

        assertThatThrownBy(() ->
                chatMessageService.sendMessage(USER_ID, ROOM_ID, new ChatSendMessageRequest("hello")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_NOT_JOINED);
    }

    @Test
    void sendMessage_userNotFound_throws_USER_NOT_FOUND() {
        // 멤버 검증 통과 → Account 미조회(탈퇴 등) → USER_NOT_FOUND
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(29));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(ROOM_ID), eq(USER_ID), any())).willReturn(true);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                chatMessageService.sendMessage(USER_ID, ROOM_ID, new ChatSendMessageRequest("hello")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void sendMessage_emptyContent_throws_INVALID_REQUEST() {
        // 멤버 검증 통과 → Account 조회 → Message 도메인 빌더에서 빈 content → INVALID_REQUEST
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(29));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(ROOM_ID), eq(USER_ID), any())).willReturn(true);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(mock(Account.class)));

        assertThatThrownBy(() ->
                chatMessageService.sendMessage(USER_ID, ROOM_ID, new ChatSendMessageRequest("")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void sendMessage_contentTooLong_throws_MESSAGE_TOO_LONG() {
        // 멤버 검증 통과 → Account 조회 → 1001자 content → Message.validateByType에서 MESSAGE_TOO_LONG
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(29));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(ROOM_ID), eq(USER_ID), any())).willReturn(true);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(mock(Account.class)));
        String over1000 = "가".repeat(1001);

        assertThatThrownBy(() ->
                chatMessageService.sendMessage(USER_ID, ROOM_ID, new ChatSendMessageRequest(over1000)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_TOO_LONG);
    }

    // ===== getMessages =====

    @Test
    void getMessages_success_returns_page_with_account_info() {
        // ACTIVE 멤버 → 메시지 목록 반환, Account 정보 포함
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        com.chunbaetour.domain.chat.entity.ChatRoomMember member = mock(com.chunbaetour.domain.chat.entity.ChatRoomMember.class);
        given(member.getMemberState()).willReturn(ChatMemberState.OWNER_ACTIVE);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        Message msg = mock(Message.class);
        given(msg.getId()).willReturn(10L);
        given(msg.getChatRoomId()).willReturn(ROOM_ID);
        given(msg.getSenderId()).willReturn(USER_ID);
        given(msg.getMessageType()).willReturn(MessageType.TEXT);
        given(msg.getContent()).willReturn("안녕");
        given(messageRepository.findWithCursor(eq(ROOM_ID), any(), any())).willReturn(List.of(msg));
        Account account = mock(Account.class);
        given(account.getId()).willReturn(USER_ID);
        given(account.getNickname()).willReturn("여행자");
        given(account.getProfileImageUrl()).willReturn("https://cdn.example.com/img.jpg");
        given(accountRepository.findAllById(List.of(USER_ID))).willReturn(List.of(account));

        CursorPageResponse<ChatMessageResponse> result = chatMessageService.getMessages(USER_ID, ROOM_ID, null, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).chatRoomId()).isEqualTo(ROOM_ID);
        assertThat(result.content().get(0).senderNickname()).isEqualTo("여행자");
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void getMessages_hasNext_true_when_more_exist() {
        // size=2, 메시지 3개 반환 → hasNext=true, nextCursor 존재
        // m3는 page에 포함 안 되므로 stub 없이 단순 mock
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        com.chunbaetour.domain.chat.entity.ChatRoomMember member = mock(com.chunbaetour.domain.chat.entity.ChatRoomMember.class);
        given(member.getMemberState()).willReturn(ChatMemberState.MEMBER_ACTIVE);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        Message m1 = stubMessage(3L), m2 = stubMessage(2L), m3 = mock(Message.class);
        given(messageRepository.findWithCursor(eq(ROOM_ID), any(), any())).willReturn(List.of(m1, m2, m3));
        given(accountRepository.findAllById(any())).willReturn(List.of());

        CursorPageResponse<ChatMessageResponse> result = chatMessageService.getMessages(USER_ID, ROOM_ID, null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    void getMessages_roomNotFound_throws_CHAT_ROOM_NOT_FOUND() {
        // 존재하지 않는 채팅방 → CHAT_ROOM_NOT_FOUND
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(false);

        assertThatThrownBy(() -> chatMessageService.getMessages(USER_ID, ROOM_ID, null, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void getMessages_notMember_throws_CHAT_NOT_JOINED() {
        // 비참여자 접근 → CHAT_NOT_JOINED
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.getMessages(USER_ID, ROOM_ID, null, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_NOT_JOINED);
    }

    @Test
    void getMessages_deletedSender_returnsFallbackNickname() {
        // Account 없는 senderId → "탈퇴한 사용자" fallback
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        com.chunbaetour.domain.chat.entity.ChatRoomMember member = mock(com.chunbaetour.domain.chat.entity.ChatRoomMember.class);
        given(member.getMemberState()).willReturn(ChatMemberState.MEMBER_ACTIVE);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        Message msg = stubMessage(5L);
        given(messageRepository.findWithCursor(eq(ROOM_ID), any(), any())).willReturn(List.of(msg));
        // Account 조회 결과 없음 — 탈퇴 계정 시나리오
        given(accountRepository.findAllById(any())).willReturn(List.of());

        CursorPageResponse<ChatMessageResponse> result = chatMessageService.getMessages(USER_ID, ROOM_ID, null, 10);

        assertThat(result.content().get(0).senderNickname()).isEqualTo("탈퇴한 사용자");
    }

    @Test
    void getMessages_withCursor_returnsMessagesBeforeCursor() {
        // cursor=100 → id < 100인 메시지만 반환
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        com.chunbaetour.domain.chat.entity.ChatRoomMember member = mock(com.chunbaetour.domain.chat.entity.ChatRoomMember.class);
        given(member.getMemberState()).willReturn(ChatMemberState.MEMBER_ACTIVE);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        Message msg = stubMessage(99L);
        given(messageRepository.findWithCursor(eq(ROOM_ID), eq(100L), any())).willReturn(List.of(msg));
        given(accountRepository.findAllById(any())).willReturn(List.of());

        String cursor = CursorUtils.encode(100L);
        CursorPageResponse<ChatMessageResponse> result = chatMessageService.getMessages(USER_ID, ROOM_ID, cursor, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).chatRoomId()).isEqualTo(ROOM_ID);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void getMessages_systemMessage_senderNicknameIsNull() {
        // SYSTEM 메시지(senderId=null) → senderNickname null
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        com.chunbaetour.domain.chat.entity.ChatRoomMember member = mock(com.chunbaetour.domain.chat.entity.ChatRoomMember.class);
        given(member.getMemberState()).willReturn(ChatMemberState.MEMBER_ACTIVE);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        Message systemMsg = mock(Message.class);
        given(systemMsg.getId()).willReturn(20L);
        given(systemMsg.getChatRoomId()).willReturn(ROOM_ID);
        given(systemMsg.getSenderId()).willReturn(null);
        given(systemMsg.getMessageType()).willReturn(MessageType.SYSTEM);
        given(systemMsg.getContent()).willReturn("채팅방이 생성되었습니다.");
        given(messageRepository.findWithCursor(eq(ROOM_ID), any(), any())).willReturn(List.of(systemMsg));
        given(accountRepository.findAllById(List.of())).willReturn(List.of());

        CursorPageResponse<ChatMessageResponse> result = chatMessageService.getMessages(USER_ID, ROOM_ID, null, 10);

        assertThat(result.content().get(0).senderNickname()).isNull();
    }

    private Message stubMessage(Long id) {
        Message msg = mock(Message.class);
        given(msg.getId()).willReturn(id);
        given(msg.getChatRoomId()).willReturn(ROOM_ID);
        given(msg.getSenderId()).willReturn(USER_ID);
        given(msg.getMessageType()).willReturn(MessageType.TEXT);
        given(msg.getContent()).willReturn("메시지" + id);
        given(msg.getCreatedAt()).willReturn(LocalDateTime.of(2026, 5, 27, 12, 0));
        return msg;
    }
}
