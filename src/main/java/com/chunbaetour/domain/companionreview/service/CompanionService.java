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
import lombok.RequiredArgsConstructor;
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

        List<ChatMemberState> activeStates = List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);
        for (Long participantId : request.participantUserIds()) {
            if (!chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(roomId, participantId, activeStates)) {
                throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
            }
        }

        Companion companion = companionRepository.save(
                Companion.builder().chatRoomId(roomId).build());

        // 방장 자동 포함 — 요청에 없으면 추가
        List<Long> allParticipantIds = new ArrayList<>(request.participantUserIds());
        if (!allParticipantIds.contains(ownerId)) {
            allParticipantIds.add(ownerId);
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

        Companion companion = companionRepository.findByChatRoomId(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_NOT_FOUND));

        companion.end();

        return CompanionEndResponse.from(companion);
    }
}
