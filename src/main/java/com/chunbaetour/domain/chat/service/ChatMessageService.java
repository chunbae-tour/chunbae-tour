package com.chunbaetour.domain.chat.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.request.ChatSendMessageRequest;
import com.chunbaetour.domain.chat.dto.response.ChatMessageResponse;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.entity.Message;
import com.chunbaetour.domain.chat.event.ChatMessageSentEvent;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.repository.MessageRepository;
import com.chunbaetour.domain.chat.storage.ChatFileKeys;
import com.chunbaetour.domain.chat.storage.ChatFileStorage;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.MessageType;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.ratelimit.RateLimitDecision;
import com.chunbaetour.domain.common.ratelimit.RateLimitPolicy;
import com.chunbaetour.domain.common.ratelimit.RateLimiter;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    // 운영 보안 정책 설계서 11번 — 채팅 메시지 전송 30회/10초
    private static final RateLimitPolicy MESSAGE_RATE_LIMIT = new RateLimitPolicy(30, Duration.ofSeconds(10));

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final AccountRepository accountRepository;
    private final MessageRepository messageRepository;
    private final ChatRedisPubSubService chatRedisPubSubService;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatFileStorage chatFileStorage;

    // 메시지 전송 — rate limit 선검증 후 ACTIVE 멤버 확인, DB 저장 및 Redis 발행
    @Transactional
    public void sendMessage(Long userId, Long chatRoomId, ChatSendMessageRequest request) {
        // rate limit 선검증 — userId 단위 30회/10초, 초과 시 COMMON_006(TOO_MANY_REQUESTS)
        // 비참여자 메시지 시도도 rate limit slot 소비 — anonymous flood 방지 의도된 동작
        RateLimitDecision decision = rateLimiter.tryConsume("ratelimit:chat-message:" + userId, MESSAGE_RATE_LIMIT);
        if (!decision.allowed()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        // senderId는 SecurityContext(STOMP principal)에서 추출 — 클라이언트 전달값 신뢰 금지
        boolean isMember = chatRoomMemberRepository
                .existsByChatRoomIdAndUserIdAndMemberStateIn(chatRoomId, userId, ChatMemberState.activeStates());
        if (!isMember) {
            throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
        }

        Account sender = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // messageType 미지정 시 TEXT(기존 클라이언트 호환). SYSTEM은 클라이언트가 지정 불가(서버 전용 타입).
        MessageType messageType = request.messageType() != null ? request.messageType() : MessageType.TEXT;
        if (messageType == MessageType.SYSTEM) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Message.MessageBuilder messageBuilder = Message.builder()
                .chatRoomId(chatRoomId)
                .senderId(userId)
                .messageType(messageType)
                .content(request.content());

        if (messageType == MessageType.IMAGE || messageType == MessageType.FILE) {
            // fileUrl 누락은 필수 필드 누락(INVALID_REQUEST) — belongsToChatRoom의 null→false와 구분해 정확한 에러코드 반환
            if (request.fileUrl() == null || request.fileUrl().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            // fileUrl(객체 키)이 이 채팅방 업로드(POST .../files)로 발급된 키인지 검증 — 타 방 키 전송 차단(IDOR)
            if (!ChatFileKeys.belongsToChatRoom(request.fileUrl(), chatRoomId)) {
                throw new BusinessException(ErrorCode.CHAT_FILE_OWNERSHIP_INVALID);
            }
            messageBuilder.fileUrl(request.fileUrl())
                    .fileName(request.fileName())
                    .fileSize(request.fileSize());
        }

        // 1000자 초과·빈 content·fileUrl/fileName/fileSize 누락 검증은 Message 도메인 메서드에서 수행
        // (MESSAGE_TOO_LONG, INVALID_REQUEST)
        Message saved = messageRepository.save(messageBuilder.build());
        ChatMessageResponse response = ChatMessageResponse.from(saved, sender, resolveFileUrl(saved));

        // 알림 수신 대상 — ACTIVE 멤버 중 발신자 제외, AFTER_COMMIT 리스너가 각자에게 알림 생성
        // 수신자 없으면(혼자 있는 방 등) 이벤트 미발행 — REQUIRES_NEW 트랜잭션 낭비 방지
        List<Long> recipientUserIds = chatRoomMemberRepository
                .findByChatRoomIdAndMemberStateInAndUserIdNot(chatRoomId, ChatMemberState.activeStates(), userId)
                .stream()
                .map(ChatRoomMember::getUserId)
                .toList();
        if (!recipientUserIds.isEmpty()) {
            eventPublisher.publishEvent(new ChatMessageSentEvent(chatRoomId, userId, recipientUserIds));
        }

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

    // 메시지 내역 조회 — ACTIVE 멤버만 접근, id DESC 커서 페이징, N+1 방지: senderId 일괄 조회
    public CursorPageResponse<ChatMessageResponse> getMessages(Long userId, Long roomId, String cursor, int size) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .filter(ChatRoomMember::isActiveMember)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_NOT_JOINED));

        Long cursorId = cursor != null ? CursorUtils.decodeSafe(cursor) : null;

        List<Message> messages = messageRepository.findWithCursor(
                roomId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = messages.size() > size;
        List<Message> page = hasNext ? messages.subList(0, size) : messages;

        String nextCursor = hasNext ? CursorUtils.encode(page.get(page.size() - 1).getId()) : null;

        // senderId null(SYSTEM 메시지) 제외 후 일괄 조회 — 탈퇴 계정은 from()에서 fallback 처리
        List<Long> senderIds = page.stream()
                .map(Message::getSenderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Account> accountMap = accountRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        return new CursorPageResponse<>(
                page.stream()
                        .map(m -> ChatMessageResponse.from(m, accountMap.get(m.getSenderId()), resolveFileUrl(m)))
                        .toList(),
                nextCursor,
                hasNext,
                size
        );
    }

    // Message.fileUrl(S3 객체 키) → presigned GET URL 변환. TEXT/SYSTEM은 fileUrl이 없어 그대로 null 반환.
    // presign 발급은 로컬 서명 연산(네트워크 호출 없음)이라 메시지별 호출해도 N+1 문제 없음.
    // EXTERNAL_SERVICE_ERROR만 fileUrl=null 격하 — 조회 전체 503 방지. 그 외 예외는 전파.
    private String resolveFileUrl(Message message) {
        if (message.getFileUrl() == null) {
            return null;
        }
        try {
            return chatFileStorage.presignedGetUrl(message.getFileUrl());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.EXTERNAL_SERVICE_ERROR) {
                log.warn("presign 실패 — fileUrl=null 격하: key={}", message.getFileUrl());
                return null;
            }
            throw e;
        }
    }
}
