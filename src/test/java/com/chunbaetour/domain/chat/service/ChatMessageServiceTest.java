package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.request.ChatSendMessageRequest;
import com.chunbaetour.domain.chat.entity.Message;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.MessageRepository;
import com.chunbaetour.domain.chat.type.MessageType;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatRedisPubSubService chatRedisPubSubService;

    @InjectMocks
    private ChatMessageService chatMessageService;

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 100L;

    @Test
    void sendMessage_success_savesAndPublishes() {
        // ACTIVE 멤버 → 저장 후 Redis 발행
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
        given(messageRepository.save(any())).willReturn(saved);

        chatMessageService.sendMessage(USER_ID, ROOM_ID, new ChatSendMessageRequest("안녕하세요"));

        then(messageRepository).should().save(any(Message.class));
        then(chatRedisPubSubService).should().publish(any(), any());
    }

    @Test
    void sendMessage_notMember_throws_CHAT_NOT_JOINED() {
        // 비참여자·강퇴·퇴장 모두 ACTIVE_STATES 미포함 → CHAT_NOT_JOINED
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(ROOM_ID), eq(USER_ID), any())).willReturn(false);

        assertThatThrownBy(() ->
                chatMessageService.sendMessage(USER_ID, ROOM_ID, new ChatSendMessageRequest("hello")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_NOT_JOINED);
    }

    @Test
    void sendMessage_emptyContent_throws_INVALID_REQUEST() {
        // 멤버 검증 통과 → Account 조회 → Message 도메인 빌더에서 빈 content → INVALID_REQUEST
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
}
