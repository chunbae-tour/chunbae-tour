package com.chunbaetour.domain.chat.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.request.CreateJoinRequestRequest;
import com.chunbaetour.domain.chat.dto.response.ApproveJoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.CreateJoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.JoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.RejectJoinRequestResponse;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.event.JoinRequestApprovedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestCreatedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestRejectedEvent;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.repository.JoinRequestRepository;
import com.chunbaetour.domain.chat.type.JoinRequestStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JoinRequestService {

    private static final String LOCK_KEY_FORMAT = "chatroom:lock:%d";
    private static final long LOCK_WAIT_SECONDS = 3L;
    // DB 작업 지연 시 watchdog 무한 점유 방지 — 정상 작업은 수백 ms 이내 완료
    private static final long LOCK_LEASE_SECONDS = 5L;
    // lease보다 짧게 — 트랜잭션이 lease 만료 전에 항상 끝나도록 강제(락 만료 후 동작 방지)
    private static final int TX_TIMEOUT_SECONDS = 4;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final AccountRepository accountRepository;
    private final RedissonClient redissonClient;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationEventPublisher eventPublisher;

    // NOT_SUPPORTED로 외부 readOnly 트랜잭션 중단 — TransactionTemplate이 새 쓰기 트랜잭션 생성
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateJoinRequestResponse createJoinRequest(
            Long userId, Long chatRoomId, CreateJoinRequestRequest request) {

        // Account 조회는 락과 무관 — 락 점유 시간 최소화
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // chatRoomId 단위 분산 락 — 상태 확인~저장 임계구역 직렬화로 TOCTOU 방지
        RLock lock = redissonClient.getLock(LOCK_KEY_FORMAT.formatted(chatRoomId));
        try {
            // 명시적 만료 시간 사용 — watchdog(-1L) 활성화 시 DB 지연·데드락으로 락이 무한 점유될 수 있음
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
            }
            // 트랜잭션 커밋이 락 해제 전에 완료되도록 TransactionTemplate 사용, 타임아웃은 lease보다 짧게 설정
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setTimeout(TX_TIMEOUT_SECONDS);
            return Objects.requireNonNull(
                    transactionTemplate.execute(
                            status -> doCreateJoinRequest(userId, chatRoomId, request, account)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        } catch (RedisException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        } finally {
            releaseLock(lock);
        }
    }

    // 방장만 조회 가능 — OWNER_ACTIVE 여부로 권한 확인 후 PENDING 목록 반환
    // N+1 방지: userId 목록 추출 후 findAllById로 계정 일괄 조회
    public List<JoinRequestResponse> getJoinRequests(Long userId, Long chatRoomId) {
        if (!chatRoomRepository.existsById(chatRoomId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        // 방장 권한 확인 — isOwner()가 아니면 열람 불가 (CHAT_006)
        chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .filter(ChatRoomMember::isOwner)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN));

        List<JoinRequest> requests = joinRequestRepository
                .findByChatRoomIdAndStatusOrderByCreatedAtAsc(chatRoomId, JoinRequestStatus.PENDING);

        // 신청자 ID 일괄 조회 — 개별 조회 시 N+1 발생하므로 IN 쿼리로 한 번에 로드
        // pending_key unique constraint로 동일 chatRoomId+userId PENDING 중복 불가 — distinct() 생략
        List<Long> userIds = requests.stream()
                .map(JoinRequest::getUserId)
                .toList();
        Map<Long, Account> accountMap = accountRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        return requests.stream()
                .map(r -> JoinRequestResponse.from(r, accountMap.get(r.getUserId())))
                .toList();
    }

    // NOT_SUPPORTED로 외부 readOnly 트랜잭션 중단 — TransactionTemplate이 새 쓰기 트랜잭션 생성
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ApproveJoinRequestResponse approveJoinRequest(Long ownerId, Long chatRoomId, Long requestId) {

        // 신청 및 방장 확인은 락 밖에서 — 락 점유 시간 최소화
        JoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_APPLICATION_NOT_FOUND));

        // 경로 chatRoomId와 신청의 chatRoomId 일치 검증 — 타 방 신청을 잘못 수락 방지
        if (!joinRequest.getChatRoomId().equals(chatRoomId)) {
            throw new BusinessException(ErrorCode.CHAT_APPLICATION_NOT_FOUND);
        }

        // 방장 권한 확인 — isOwner()가 아니면 수락 불가 (CHAT_006)
        chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoomId, ownerId)
                .filter(ChatRoomMember::isOwner)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN));

        // chatRoomId 단위 분산 락 — incrementMembers() TOCTOU 방지
        RLock lock = redissonClient.getLock(LOCK_KEY_FORMAT.formatted(chatRoomId));
        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
            }
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setTimeout(TX_TIMEOUT_SECONDS);
            return Objects.requireNonNull(
                    transactionTemplate.execute(
                            status -> doApproveJoinRequest(ownerId, chatRoomId, requestId)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        } catch (RedisException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        } finally {
            releaseLock(lock);
        }
    }

    // 참여 신청 취소 — 신청자 본인만 가능, WHERE status=PENDING 조건부 원자적 삭제로 approve 경합 차단
    @Transactional
    public void cancelJoinRequest(Long userId, Long chatRoomId, Long joinRequestId) {
        JoinRequest joinRequest = joinRequestRepository.findById(joinRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_APPLICATION_NOT_FOUND));

        // 경로 chatRoomId와 신청의 chatRoomId 일치 검증 — 타 방 신청 조작 방지
        if (!joinRequest.getChatRoomId().equals(chatRoomId)) {
            throw new BusinessException(ErrorCode.CHAT_APPLICATION_NOT_FOUND);
        }

        // 본인 신청만 취소 가능 — userId 기준 equals 호출 (NPE 방지)
        if (!userId.equals(joinRequest.getUserId())) {
            throw new BusinessException(ErrorCode.CHAT_NOT_APPLICANT);
        }

        // WHERE status=PENDING 조건부 원자적 삭제 — approve와 동시 경합 시 이미 처리된 신청으로 차단 (CHAT_012)
        if (joinRequestRepository.deleteIfPending(joinRequestId) == 0) {
            throw new BusinessException(ErrorCode.CHAT_APPLICATION_ALREADY_PROCESSED);
        }
    }

    // 거절 — 방장 전용, WHERE status='PENDING' 조건부 UPDATE로 분산 락 없이 이중 거절 원자적 차단
    @Transactional
    public RejectJoinRequestResponse rejectJoinRequest(Long ownerId, Long chatRoomId, Long requestId) {
        chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoomId, ownerId)
                .filter(ChatRoomMember::isOwner)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN));

        // SELECT FOR UPDATE — approve와 동일 행 잠금으로 approve↔reject 경합 직렬화
        JoinRequest joinRequest = joinRequestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_APPLICATION_NOT_FOUND));

        if (!joinRequest.getChatRoomId().equals(chatRoomId)) {
            throw new BusinessException(ErrorCode.CHAT_APPLICATION_NOT_FOUND);
        }

        // WHERE status='PENDING' 조건부 원자적 UPDATE — 영향 행 0이면 이미 처리된 신청 (CHAT_012)
        if (joinRequestRepository.rejectIfPending(requestId) == 0) {
            throw new BusinessException(ErrorCode.CHAT_APPLICATION_ALREADY_PROCESSED);
        }

        joinRequest.reject();

        // 트랜잭션 커밋 후 신청자에게 알림 — AFTER_COMMIT 리스너가 REQUIRES_NEW 트랜잭션으로 저장
        eventPublisher.publishEvent(
                new JoinRequestRejectedEvent(chatRoomId, requestId, joinRequest.getUserId()));

        return RejectJoinRequestResponse.from(joinRequest);
    }

    // 락 해제 — isHeldByCurrentThread()/unlock() 자체가 Redis 장애로 던지는 RedisException이
    // try/catch에서 던진 원본 예외를 덮어쓰지 않도록 여기서 잡아 로그만 남김
    private void releaseLock(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RedisException e) {
            log.warn("락 해제 실패 — Redis 장애로 추정, lease 만료로 자동 해제됨", e);
        }
    }

    // 락 내부 실제 비즈니스 로직 — TransactionTemplate으로 호출해 트랜잭션 커밋이 락 해제 전에 완료됨을 보장
    private CreateJoinRequestResponse doCreateJoinRequest(
            Long userId, Long chatRoomId, CreateJoinRequestRequest request, Account account) {

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        chatRoom.validateJoinable();

        // 멤버 이력 단일 조회 — 강퇴 이력·활성 참여 여부를 쿼리 1번으로 확인
        chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .ifPresent(member -> {
                    if (member.isKicked()) {
                        throw new BusinessException(ErrorCode.CHAT_MEMBER_KICKED_REJOIN);
                    }
                    if (member.isActiveMember()) {
                        throw new BusinessException(ErrorCode.ALREADY_JOINED_CHAT);
                    }
                });

        // 중복 신청 확인 — PENDING 상태 신청이 이미 존재하면 차단 (CHAT_004)
        if (joinRequestRepository.existsByChatRoomIdAndUserIdAndStatus(
                chatRoomId, userId, JoinRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.ALREADY_APPLIED_CHAT);
        }

        JoinRequest joinRequest = JoinRequest.builder()
                .chatRoomId(chatRoomId)
                .userId(userId)
                .message(request.message())
                .build();
        // save() 반환값 사용 — JPA가 DB 생성 ID를 채운 managed 엔티티 반환
        JoinRequest saved = joinRequestRepository.save(joinRequest);

        // publishEvent는 TransactionTemplate 트랜잭션에 바인딩 — 이 메서드는 NOT_SUPPORTED 경계 내
        // TransactionTemplate.execute()로 호출되므로 AFTER_COMMIT이 TransactionTemplate TX 커밋 후 발화 (의도된 동작)
        // 향후 외부 트랜잭션이 추가되면 이벤트가 잘못된 TX에 바인딩될 수 있으므로 호출 구조 변경 시 재검토 필요
        eventPublisher.publishEvent(
                new JoinRequestCreatedEvent(chatRoomId, saved.getId(), chatRoom.getOwnerId()));

        return CreateJoinRequestResponse.from(saved, account);
    }

    // 락 내부 수락 로직 — 최신 상태 재조회 후 approve() 호출, ChatRoomMember 생성 및 정원 증가
    private ApproveJoinRequestResponse doApproveJoinRequest(Long ownerId, Long chatRoomId, Long requestId) {

        // SELECT FOR UPDATE — 락 획득 후 최신 상태 재조회, reject와 동일 행 잠금으로 approve↔reject 경합 직렬화
        JoinRequest joinRequest = joinRequestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_APPLICATION_NOT_FOUND));

        // 락 내부 재검증 — 락 획득 전 검증과 실제 상태 변경 사이 방장 교체 방지
        if (!joinRequest.getChatRoomId().equals(chatRoomId)) {
            throw new BusinessException(ErrorCode.CHAT_APPLICATION_NOT_FOUND);
        }
        chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoomId, ownerId)
                .filter(ChatRoomMember::isOwner)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN));

        // PENDING 아니면 BusinessException(CHAT_012) 발생
        joinRequest.approve();

        // 비관적 락으로 조회 — Redis 장애 시 DB 단독으로 정원 정합성 보장
        ChatRoom chatRoom = chatRoomRepository.findByIdWithLock(joinRequest.getChatRoomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // currentMembers +1, 정원 도달 시 자동으로 FULL 전환
        chatRoom.incrementMembers();

        // LEFT 이력 있으면 재활성화, 없으면 신규 생성 — unique(chat_room_id, user_id) 제약 준수
        chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoom.getId(), joinRequest.getUserId())
                .ifPresentOrElse(
                        ChatRoomMember::reactivate,
                        () -> chatRoomMemberRepository.save(
                                ChatRoomMember.ofMember(chatRoom, joinRequest.getUserId())));

        // publishEvent는 TransactionTemplate 트랜잭션에 바인딩 — doCreateJoinRequest와 동일한 구조
        // 향후 외부 트랜잭션 추가 시 바인딩 대상 재검토 필요
        eventPublisher.publishEvent(
                new JoinRequestApprovedEvent(chatRoomId, requestId, joinRequest.getUserId()));

        return ApproveJoinRequestResponse.from(joinRequest, chatRoom.getCurrentMembers());
    }
}
