package com.chunbaetour.domain.cs.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.cs.dto.request.SupportRoomCloseRequest;
import com.chunbaetour.domain.cs.dto.request.SupportRoomCreateRequest;
import com.chunbaetour.domain.cs.dto.response.AdminSupportRoomResponse;
import com.chunbaetour.domain.cs.dto.response.AdminSupportRoomResponse.LastMessage;
import com.chunbaetour.domain.cs.dto.response.SupportMessageResponse;
import com.chunbaetour.domain.cs.dto.response.SupportRoomResponse;
import com.chunbaetour.domain.cs.entity.SupportMessage;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import com.chunbaetour.domain.cs.event.SupportRoomClosedEvent;
import com.chunbaetour.domain.cs.repository.SupportMessageRepository;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportRoomService {

    private final Clock clock;
    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    // 상담방 생성 (USER·MERCHANT) — 활성 방 중복 차단(앱 레벨 선체크 + DB unique 제약 이중 방어)
    @Transactional
    public SupportRoomResponse createRoom(Long userId, SupportRoomCreateRequest request) {
        if (supportRoomRepository.existsByUserIdAndStatusIn(
                userId, List.of(SupportRoomStatus.WAITING, SupportRoomStatus.IN_PROGRESS))) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_EXISTS);
        }

        SupportRoom room;
        try {
            room = supportRoomRepository.save(SupportRoom.builder().userId(userId).build());
        } catch (DataIntegrityViolationException e) {
            // uk_support_rooms_active_user 위반 — 동시 요청으로 앱 레벨 체크를 통과한 경우
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_EXISTS);
        }

        if (request.initialMessage() != null && !request.initialMessage().isBlank()) {
            supportMessageRepository.save(
                    SupportMessage.builder()
                            .supportRoomId(room.getId())
                            .senderId(userId)
                            .senderRole(SupportSenderRole.CUSTOMER)
                            .messageType(SupportMessageType.TEXT)
                            .content(request.initialMessage())
                            .fileUrl(null)
                            .build()
            );
        }

        return SupportRoomResponse.fromForUser(room);
    }

    // USER 본인 상담방 목록 cursor 페이징 — status 필터 선택
    public CursorPageResponse<SupportRoomResponse> getMyRooms(Long userId, String cursor, int size, SupportRoomStatus status) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<SupportRoom> page = supportRoomRepository.findMyRoomsWithCursor(
                userId, status, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = page.size() > size;
        List<SupportRoomResponse> content = page.stream().limit(size).map(SupportRoomResponse::fromForUser).toList();
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).supportRoomId()) : null;

        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }

    // USER 상담방 메시지 cursor 페이징 — 본인 방만 접근 가능, id DESC (최신 먼저)
    public CursorPageResponse<SupportMessageResponse> getMessages(Long userId, Long supportRoomId, String cursor, int size) {
        SupportRoom room = supportRoomRepository.findById(supportRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_ROOM_NOT_FOUND));

        if (!room.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_FORBIDDEN);
        }

        return fetchMessages(supportRoomId, cursor, size);
    }

    // ADMIN 상담방 메시지 cursor 페이징 — 소유자 무관 접근 가능, id DESC (최신 먼저)
    public CursorPageResponse<SupportMessageResponse> getMessagesAsAdmin(Long supportRoomId, String cursor, int size) {
        if (!supportRoomRepository.existsById(supportRoomId)) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_NOT_FOUND);
        }
        return fetchMessages(supportRoomId, cursor, size);
    }

    // ADMIN 상담방 배정 — 호출한 ADMIN이 담당자로 배정됨, WAITING→IN_PROGRESS 전이
    @Transactional
    public SupportRoomResponse assignAdmin(Long supportRoomId, Long adminId) {
        SupportRoom room = supportRoomRepository.findById(supportRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_ROOM_NOT_FOUND));
        if (room.getStatus() == SupportRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED);
        }
        if (room.getStatus() == SupportRoomStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_ASSIGNED);
        }
        room.assignAdmin(adminId);
        return SupportRoomResponse.from(room);
    }

    // ADMIN 상담 종료 — CLOSED 재종료 시 CS_002, 존재하지 않으면 CS_001
    @Transactional
    public SupportRoomResponse closeRoom(Long supportRoomId, SupportRoomCloseRequest request) {
        SupportRoom room = supportRoomRepository.findById(supportRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_ROOM_NOT_FOUND));
        room.close(clock, request.summary());
        // 종료 알림 — 방 소유자에게 SUPPORT_ROOM_CLOSED 알림
        applicationEventPublisher.publishEvent(new SupportRoomClosedEvent(supportRoomId, room.getUserId()));
        return SupportRoomResponse.from(room);
    }

    // ADMIN 전체 상담방 목록 cursor 페이징 — userNickname + lastMessage 배치 조회(N+1 방지)
    public CursorPageResponse<AdminSupportRoomResponse> getAllRooms(String cursor, int size, SupportRoomStatus status) {
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<SupportRoom> page = supportRoomRepository.findAllRoomsWithCursor(
                status, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = page.size() > size;
        List<SupportRoom> rooms = page.stream().limit(size).toList();

        if (rooms.isEmpty()) {
            return new CursorPageResponse<>(List.of(), null, false, 0);
        }

        List<Long> userIds = rooms.stream().map(SupportRoom::getUserId).toList();
        List<Long> roomIds = rooms.stream().map(SupportRoom::getId).toList();

        // 닉네임 배치 조회
        Map<Long, String> nicknameByUserId = accountRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(Account::getId, Account::getNickname));

        // 마지막 메시지 배치 조회 — 빈 리스트는 위에서 early return으로 차단
        Map<Long, SupportMessage> lastMsgByRoomId = supportMessageRepository
                .findLastMessagesByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(SupportMessage::getSupportRoomId, m -> m));

        List<AdminSupportRoomResponse> content = rooms.stream().map(room -> {
            String nickname = nicknameByUserId.getOrDefault(room.getUserId(), "(탈퇴한 사용자)");
            SupportMessage lastMsg = lastMsgByRoomId.get(room.getId());
            LastMessage lastMessage = lastMsg != null ? new LastMessage(resolveLastMessageContent(lastMsg), lastMsg.getSentAt()) : null;
            return AdminSupportRoomResponse.of(room, nickname, lastMessage);
        }).toList();

        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).supportRoomId()) : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }

    // exhaustive switch — 새 SupportMessageType 추가 시 컴파일 오류로 누락 방지
    private String resolveLastMessageContent(SupportMessage msg) {
        return switch (msg.getMessageType()) {
            case TEXT -> msg.getContent() != null ? msg.getContent() : "";
            case IMAGE -> "[이미지]";
            case FILE -> "[파일]";
        };
    }

    private CursorPageResponse<SupportMessageResponse> fetchMessages(Long supportRoomId, String cursor, int size) {
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<SupportMessage> page = supportMessageRepository.findMessagesWithCursor(
                supportRoomId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = page.size() > size;
        List<SupportMessageResponse> content = page.stream().limit(size).map(SupportMessageResponse::from).toList();
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).messageId()) : null;

        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }
}
