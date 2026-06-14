package com.chunbaetour.domain.companionreview.service;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companionreview.dto.request.CompanionAddParticipantsRequest;
import com.chunbaetour.domain.companionreview.dto.request.CompanionCreateRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionAddParticipantsResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionCreateResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionEndResponse;
import com.chunbaetour.domain.companionreview.entity.Companion;
import com.chunbaetour.domain.companionreview.entity.CompanionParticipant;
import com.chunbaetour.domain.companionreview.repository.CompanionParticipantRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionTripPeriodProjection;
import com.chunbaetour.domain.companionreview.type.CompanionStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionService {

    // userId 단위 락 — 같은 유저가 겹치는 기간으로 동시에 동행 생성/참여 시 overlap 체크(CR_010) TOCTOU 방지
    private static final String USER_TRIP_LOCK_KEY_FORMAT = "companion:trip-overlap:user:%d";
    private static final long LOCK_WAIT_SECONDS = 3L;
    // DB 작업 지연 시 watchdog 무한 점유 방지 — 정상 작업은 수백 ms 이내 완료
    private static final long LOCK_LEASE_SECONDS = 5L;
    // lease보다 짧게 — 트랜잭션이 lease 만료 전에 항상 끝나도록 강제(락 만료 후 동작 방지)
    private static final int TX_TIMEOUT_SECONDS = 4;

    private final CompanionRepository companionRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final RedissonClient redissonClient;
    private final PlatformTransactionManager transactionManager;

    // 동행 생성 — 락 획득 전 사전 검증(채팅방/방장/CLOSED/기존 동행/ACTIVE 멤버십) 후 방장+참여자 전원에 분산 락 건 뒤 본 로직 실행
    // (NOT_SUPPORTED로 외부 트랜잭션 중단, TransactionTemplate이 새 트랜잭션 생성)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompanionCreateResponse createCompanion(Long ownerId, Long roomId, CompanionCreateRequest request) {
        // tripEndDate < tripStartDate → 400 (KAN-296)
        if (request.tripEndDate().isBefore(request.tripStartDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 중복 제거 — 클라이언트 중복 전송 시 uq_companion_participant 위반 방지
        List<Long> distinctParticipantIds = request.participantUserIds().stream()
                .distinct()
                .collect(Collectors.toList());

        // 방장 자동 포함 — 요청에 없으면 추가
        List<Long> allParticipantIds = new ArrayList<>(distinctParticipantIds);
        if (!allParticipantIds.contains(ownerId)) {
            allParticipantIds.add(ownerId);
        }

        // overlap TOCTOU와 무관한 검증은 락 획득 전에 끝내 불필요한 Redis 호출/락 점유 시간 줄임
        validateCreatePreconditions(ownerId, roomId, allParticipantIds);

        // 방장+참여자 전원 lock — 동시에 겹치는 기간으로 동행 생성/참여 시도 시 overlap 체크(CR_010) TOCTOU 방지
        RLock lock = redissonClient.getMultiLock(buildUserLocks(allParticipantIds));
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
                            status -> doCreateCompanion(roomId, request, allParticipantIds)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        } catch (RedisException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        } finally {
            releaseLock(lock);
        }
    }

    // 락 획득 전 사전 검증 — 채팅방 존재/방장 권한/CLOSED 여부, 기존 동행 존재(CR_004/CR_007), 참여자 ACTIVE 멤버십
    private void validateCreatePreconditions(Long ownerId, Long roomId, List<Long> allParticipantIds) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN);
        }
        if (chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }

        // 같은 방에 기존 동행 존재 → status로 분기 (ENDED: CR_004 재시작 불가, ONGOING: CR_007 이미 진행 중)
        companionRepository.findByChatRoomId(roomId).ifPresent(existing -> {
            if (existing.getStatus() == CompanionStatus.ENDED) {
                throw new BusinessException(ErrorCode.COMPANION_ALREADY_EXISTS);
            }
            throw new BusinessException(ErrorCode.COMPANION_ALREADY_STARTED);
        });

        // 방장+참여자 ACTIVE 멤버십 일괄 검증 — IN절 단일 쿼리로 N+1 방지
        List<ChatMemberState> activeStates = List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);
        long activeCount = chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(
                roomId, allParticipantIds, activeStates);
        if (activeCount != allParticipantIds.size()) {
            throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
        }
    }

    // 락 내부 — 기간 겹침 검증(CR_010) 후 저장
    private CompanionCreateResponse doCreateCompanion(Long roomId, CompanionCreateRequest request,
            List<Long> allParticipantIds) {
        // 방장+참여자 전원의 다른 ONGOING 동행과 기간 겹침 검증 — CR_010 (생성 전이므로 excludeCompanionId 없음)
        validateNoDateOverlap(allParticipantIds, request.tripStartDate(), request.tripEndDate(), null);

        Companion companion;
        try {
            companion = companionRepository.save(
                    Companion.builder()
                            .chatRoomId(roomId)
                            .tripStartDate(request.tripStartDate())
                            .tripEndDate(request.tripEndDate())
                            .build());
        } catch (DataIntegrityViolationException e) {
            // uq_companions_chat_room_id 위반 — TOCTOU 동시 요청이 앱 레벨 체크 통과한 경우
            Throwable cause = e.getCause();
            if (cause instanceof ConstraintViolationException cve
                    && "uq_companions_chat_room_id".equals(cve.getConstraintName())) {
                throw new BusinessException(ErrorCode.COMPANION_ALREADY_STARTED);
            }
            throw e;
        }

        List<CompanionParticipant> participants = allParticipantIds.stream()
                .map(userId -> CompanionParticipant.builder()
                        .companionId(companion.getId())
                        .userId(userId)
                        .build())
                .toList();
        companionParticipantRepository.saveAll(participants);

        return CompanionCreateResponse.of(companion, allParticipantIds);
    }

    // 기간 겹침 검증 — userIds 중 한 명이라도 다른 ONGOING 동행과 [tripStartDate, tripEndDate]가 겹치면 CR_010
    private void validateNoDateOverlap(List<Long> userIds, LocalDate tripStartDate, LocalDate tripEndDate,
            Long excludeCompanionId) {
        List<CompanionTripPeriodProjection> ongoingTripPeriods = companionParticipantRepository
                .findOngoingTripPeriodsByUserIds(userIds, excludeCompanionId);
        boolean hasOverlap = ongoingTripPeriods.stream()
                .anyMatch(p -> !tripStartDate.isAfter(p.getTripEndDate()) && !tripEndDate.isBefore(p.getTripStartDate()));
        if (hasOverlap) {
            throw new BusinessException(ErrorCode.COMPANION_DATE_OVERLAP);
        }
    }

    // 동행 참여자 추가 — 락 획득 전 사전 검증(채팅방/방장/CLOSED/ACTIVE 멤버십) 후 추가 대상 전원에 분산 락 건 뒤 본 로직 실행
    // (NOT_SUPPORTED로 외부 트랜잭션 중단, TransactionTemplate이 새 트랜잭션 생성)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompanionAddParticipantsResponse addParticipants(Long ownerId, Long roomId,
            CompanionAddParticipantsRequest request) {
        // 중복 제거 — 클라이언트 중복 전송 시 uq_companion_participant 위반 방지
        List<Long> distinctUserIds = request.userIds().stream()
                .distinct()
                .collect(Collectors.toList());

        // overlap TOCTOU와 무관한 검증은 락 획득 전에 끝내 불필요한 Redis 호출/락 점유 시간 줄임
        validateAddParticipantsPreconditions(ownerId, roomId, distinctUserIds);

        // 추가 대상 전원 lock — 동시에 겹치는 기간으로 동행 생성/참여 시도 시 overlap 체크(CR_010) TOCTOU 방지
        RLock lock = redissonClient.getMultiLock(buildUserLocks(distinctUserIds));
        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
            }
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setTimeout(TX_TIMEOUT_SECONDS);
            return Objects.requireNonNull(
                    transactionTemplate.execute(
                            status -> doAddParticipants(roomId, distinctUserIds)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        } catch (RedisException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        } finally {
            releaseLock(lock);
        }
    }

    // 락 획득 전 사전 검증 — 채팅방 존재/방장 권한/CLOSED 여부, 추가 대상 ACTIVE 멤버십
    private void validateAddParticipantsPreconditions(Long ownerId, Long roomId, List<Long> distinctUserIds) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN);
        }
        if (chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }

        // 추가 대상 ACTIVE 멤버십 일괄 검증 — IN절 단일 쿼리로 N+1 방지
        List<ChatMemberState> activeStates = List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);
        long activeCount = chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(
                roomId, distinctUserIds, activeStates);
        if (activeCount != distinctUserIds.size()) {
            throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
        }
    }

    // 락 내부 — ONGOING 확인(CR_006), 기간 겹침 검증(CR_010) 후 저장, 중복 시 CR_008
    private CompanionAddParticipantsResponse doAddParticipants(Long roomId, List<Long> distinctUserIds) {
        // PESSIMISTIC_WRITE — endCompanion과 동일 락으로 직렬화, ENDED 동행에 참여자 추가되는 race 방지
        Companion companion = companionRepository.findByChatRoomIdWithLock(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_NOT_FOUND));

        if (companion.getStatus() == CompanionStatus.ENDED) {
            throw new BusinessException(ErrorCode.COMPANION_ALREADY_ENDED);
        }

        // 추가 대상 전원의 다른 ONGOING 동행과 기간 겹침 검증 — CR_010 (본인 동행은 excludeCompanionId로 제외)
        validateNoDateOverlap(distinctUserIds, companion.getTripStartDate(), companion.getTripEndDate(), companion.getId());

        List<CompanionParticipant> participants = distinctUserIds.stream()
                .map(userId -> CompanionParticipant.builder()
                        .companionId(companion.getId())
                        .userId(userId)
                        .build())
                .toList();

        // CompanionParticipant는 GenerationType.IDENTITY라 saveAll() 호출 시 즉시 INSERT됨 — saveAllAndFlush 불필요
        try {
            companionParticipantRepository.saveAll(participants);
        } catch (DataIntegrityViolationException e) {
            // uq_companion_participant 위반일 때만 CR_008, 그 외 제약 위반은 그대로 전파
            Throwable cause = e.getCause();
            if (cause instanceof ConstraintViolationException cve
                    && "uq_companion_participant".equals(cve.getConstraintName())) {
                throw new BusinessException(ErrorCode.COMPANION_PARTICIPANT_ALREADY_EXISTS);
            }
            throw e;
        }

        return new CompanionAddParticipantsResponse(companion.getId(), distinctUserIds);
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

    // userId별 락 배열 생성 — 정렬 후 MultiLock 구성으로 데드락 가능성 축소
    private RLock[] buildUserLocks(List<Long> userIds) {
        return userIds.stream()
                .sorted()
                .map(userId -> redissonClient.getLock(USER_TRIP_LOCK_KEY_FORMAT.formatted(userId)))
                .toArray(RLock[]::new);
    }

    // 동행 종료 — 방장 검증, 동행 존재 확인, ONGOING 상태 확인(CR_006)
    @Transactional
    public CompanionEndResponse endCompanion(Long ownerId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN);
        }

        // PESSIMISTIC_WRITE — 동시 종료 요청 둘 다 ONGOING 읽고 end() 호출하는 경쟁 방어
        Companion companion = companionRepository.findByChatRoomIdWithLock(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_NOT_FOUND));

        companion.end();

        return CompanionEndResponse.from(companion);
    }

    // 동행 취소 — 방장 검증, ONGOING 확인(CR_006), Companion + 참여자 하드 삭제 (이력 미보존 — 취소 후 동일 채팅방에서 신규 동행 생성 가능)
    // CLOSED 체크 없음(의도) — 채팅방 상태와 무관하게 ONGOING 동행은 항상 취소 가능해야 함
    @Transactional
    public void cancelCompanion(Long ownerId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN);
        }

        // PESSIMISTIC_WRITE — endCompanion/addParticipants와 동일 락으로 직렬화
        Companion companion = companionRepository.findByChatRoomIdWithLock(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_NOT_FOUND));

        if (companion.getStatus() == CompanionStatus.ENDED) {
            throw new BusinessException(ErrorCode.COMPANION_ALREADY_ENDED);
        }

        companionParticipantRepository.deleteByCompanionId(companion.getId());
        companionRepository.delete(companion);
    }
}
