package com.chunbaetour.domain.chat.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.request.CreateJoinRequestRequest;
import com.chunbaetour.domain.chat.dto.response.ApproveJoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.CreateJoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.JoinRequestResponse;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.repository.JoinRequestRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JoinRequestService {

    private static final List<ChatMemberState> ACTIVE_STATES =
            List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);
    private static final String LOCK_KEY_FORMAT = "chatroom:lock:%d";
    private static final long LOCK_WAIT_SECONDS = 3L;
    // DB 작업 지연 시 watchdog 무한 점유 방지 — 정상 작업은 수백 ms 이내 완료
    private static final long LOCK_LEASE_SECONDS = 5L;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final AccountRepository accountRepository;
    private final RedissonClient redissonClient;
    private final PlatformTransactionManager transactionManager;

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
            // 트랜잭션 커밋이 락 해제 전에 완료되도록 TransactionTemplate 사용
            return Objects.requireNonNull(
                    new TransactionTemplate(transactionManager).execute(
                            status -> doCreateJoinRequest(userId, chatRoomId, request, account)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
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
            return Objects.requireNonNull(
                    new TransactionTemplate(transactionManager).execute(
                            status -> doApproveJoinRequest(requestId)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
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
                    if (member.getMemberState() == ChatMemberState.MEMBER_KICKED) {
                        throw new BusinessException(ErrorCode.CHAT_MEMBER_KICKED_REJOIN);
                    }
                    if (ACTIVE_STATES.contains(member.getMemberState())) {
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

        return CreateJoinRequestResponse.from(saved, account);
    }

    // 락 내부 수락 로직 — 최신 상태 재조회 후 approve() 호출, ChatRoomMember 생성 및 정원 증가
    private ApproveJoinRequestResponse doApproveJoinRequest(Long requestId) {

        // 락 획득 후 최신 상태 재조회 — 동시 수락 시 이미 처리된 신청 차단 (CHAT_012)
        JoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_APPLICATION_NOT_FOUND));

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

        return ApproveJoinRequestResponse.from(joinRequest, chatRoom.getCurrentMembers());
    }
}
