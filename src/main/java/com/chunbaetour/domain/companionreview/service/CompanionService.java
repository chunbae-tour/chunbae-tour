package com.chunbaetour.domain.companionreview.service;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companionreview.dto.request.CompanionStartRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionEndResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionStartResponse;
import com.chunbaetour.domain.companionreview.entity.Companion;
import com.chunbaetour.domain.companionreview.entity.CompanionParticipant;
import com.chunbaetour.domain.companionreview.repository.CompanionParticipantRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionRepository;
import com.chunbaetour.domain.companionreview.type.CompanionStatus;
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

    // 동행 시작 — 방장 검증, 중복 방지(CR_004/CR_007), 참여자 ACTIVE 멤버 검증, 방장 자동 포함
    @Transactional
    public CompanionStartResponse startCompanion(Long ownerId, Long roomId, CompanionStartRequest request) {
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

        Companion companion;
        try {
            companion = companionRepository.save(
                    Companion.builder().chatRoomId(roomId).build());
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

        return CompanionStartResponse.of(companion, allParticipantIds);
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
