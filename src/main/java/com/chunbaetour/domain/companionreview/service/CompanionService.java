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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionService {

    private final CompanionRepository companionRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    // 동행 생성 — 방장 검증, 중복 방지(CR_004/CR_007), 참여자 ACTIVE 멤버 검증, 방장 자동 포함, 기간 겹침 검증(CR_010)
    @Transactional
    public CompanionCreateResponse createCompanion(Long ownerId, Long roomId, CompanionCreateRequest request) {
        // tripEndDate < tripStartDate → 400 (KAN-296)
        if (request.tripEndDate().isBefore(request.tripStartDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

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

        // 중복 제거 — 클라이언트 중복 전송 시 uq_companion_participant 위반 방지
        List<Long> distinctParticipantIds = request.participantUserIds().stream()
                .distinct()
                .collect(Collectors.toList());

        // 방장 자동 포함 — 요청에 없으면 추가
        List<Long> allParticipantIds = new ArrayList<>(distinctParticipantIds);
        if (!allParticipantIds.contains(ownerId)) {
            allParticipantIds.add(ownerId);
        }

        // 방장+참여자 ACTIVE 멤버십 일괄 검증 — IN절 단일 쿼리로 N+1 방지
        List<ChatMemberState> activeStates = List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);
        long activeCount = chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(
                roomId, allParticipantIds, activeStates);
        if (activeCount != allParticipantIds.size()) {
            throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
        }

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

    // 동행 참여자 추가 — 방장 검증, CLOSED 차단, ONGOING 확인(CR_006), ACTIVE 멤버 확인, 중복 시 CR_008
    @Transactional
    public CompanionAddParticipantsResponse addParticipants(Long ownerId, Long roomId,
            CompanionAddParticipantsRequest request) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN);
        }
        if (chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }

        // PESSIMISTIC_WRITE — endCompanion과 동일 락으로 직렬화, ENDED 동행에 참여자 추가되는 race 방지
        Companion companion = companionRepository.findByChatRoomIdWithLock(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_NOT_FOUND));

        if (companion.getStatus() == CompanionStatus.ENDED) {
            throw new BusinessException(ErrorCode.COMPANION_ALREADY_ENDED);
        }

        List<Long> distinctUserIds = request.userIds().stream()
                .distinct()
                .collect(Collectors.toList());

        // 추가 대상 ACTIVE 멤버십 일괄 검증 — IN절 단일 쿼리로 N+1 방지
        List<ChatMemberState> activeStates = List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);
        long activeCount = chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(
                roomId, distinctUserIds, activeStates);
        if (activeCount != distinctUserIds.size()) {
            throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
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
}
