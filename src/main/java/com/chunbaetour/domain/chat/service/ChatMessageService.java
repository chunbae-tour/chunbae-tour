package com.chunbaetour.domain.chat.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.request.ChatSendMessageRequest;
import com.chunbaetour.domain.chat.dto.response.ChatMessageResponse;
import com.chunbaetour.domain.chat.entity.Message;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.MessageRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.MessageType;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.ratelimit.RateLimitDecision;
import com.chunbaetour.domain.common.ratelimit.RateLimitPolicy;
import com.chunbaetour.domain.common.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    // 운영 보안 정책 설계서 11번 — 채팅 메시지 전송 30회/10초
    private static final RateLimitPolicy MESSAGE_RATE_LIMIT = new RateLimitPolicy(30, Duration.ofSeconds(10));

    private static final List<ChatMemberState> ACTIVE_STATES =
            List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final AccountRepository accountRepository;
    private final MessageRepository messageRepository;
    private final ChatRedisPubSubService chatRedisPubSubService;
    private final RateLimiter rateLimiter;

    // 메시지 전송 — rate limit 선검증 후 ACTIVE 멤버 확인, DB 저장 및 Redis 발행
    @Transactional
    public void sendMessage(Long userId, Long chatRoomId, ChatSendMessageRequest request) {
        // rate limit 선검증 — userId 단위 30회/10초, 초과 시 COMMON_006(TOO_MANY_REQUESTS)
        RateLimitDecision decision = rateLimiter.tryConsume("ratelimit:chat-message:" + userId, MESSAGE_RATE_LIMIT);
        if (!decision.allowed()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        // senderId는 SecurityContext(STOMP principal)에서 추출 — 클라이언트 전달값 신뢰 금지
        boolean isMember = chatRoomMemberRepository
                .existsByChatRoomIdAndUserIdAndMemberStateIn(chatRoomId, userId, ACTIVE_STATES);
        if (!isMember) {
            throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
        }

        Account sender = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 1000자 초과·빈 content 검증은 Message 도메인 메서드에서 수행 (MESSAGE_TOO_LONG, INVALID_REQUEST)
        Message message = Message.builder()
                .chatRoomId(chatRoomId)
                .senderId(userId)
                .messageType(MessageType.TEXT)
                .content(request.content())
                .build();

        Message saved = messageRepository.save(message);
        ChatMessageResponse response = ChatMessageResponse.from(saved, sender);

        // DB 커밋 이후 발행 — 커밋 실패·롤백 시 유령 메시지 브로드캐스트 방지
        // isActualTransactionActive: 실트랜잭션 없는 컨텍스트(단위 테스트 등) → 즉시 발행 fallback
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    chatRedisPubSubService.publish(chatRoomId, response);
                }
            });
        } else {
            chatRedisPubSubService.publish(chatRoomId, response);
        }
    }
}
