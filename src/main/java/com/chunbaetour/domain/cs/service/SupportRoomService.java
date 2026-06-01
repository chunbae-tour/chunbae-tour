package com.chunbaetour.domain.cs.service;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
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
import com.chunbaetour.domain.cs.repository.SupportMessageRepository;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportRoomService {

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final AccountRepository accountRepository;

    // 상담방 생성 (USER·MERCHANT) — WAITING/IN_PROGRESS 중복 차단, initialMessage 제공 시 첫 메시지(TEXT) 함께 저장
    @Transactional
    public SupportRoomResponse createRoom(Long userId, SupportRoomCreateRequest request) {
        if (supportRoomRepository.existsByUserIdAndStatusIn(
                userId, List.of(SupportRoomStatus.WAITING, SupportRoomStatus.IN_PROGRESS))) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_EXISTS);
        }
        SupportRoom room = supportRoomRepository.save(
                SupportRoom.builder().userId(userId).build()
        );

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

        return SupportRoomResponse.from(room);
    }

    // USER 본인 상담방 목록 cursor 페이징 — status 필터 선택
    public CursorPageResponse<SupportRoomResponse> getMyRooms(Long userId, String cursor, int size, SupportRoomStatus status) {
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<SupportRoom> page = supportRoomRepository.findMyRoomsWithCursor(
                userId, status, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = page.size() > size;
        List<SupportRoomResponse> content = page.stream().limit(size).map(SupportRoomResponse::from).toList();
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).supportRoomId()) : null;

        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }

    // 상담방 메시지 cursor 페이징 — 본인(USER) 또는 ADMIN만 접근 가능
    public CursorPageResponse<SupportMessageResponse> getMessages(Long userId, boolean isAdmin, Long supportRoomId, String cursor, int size) {
        SupportRoom room = supportRoomRepository.findById(supportRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_ROOM_NOT_FOUND));

        if (!isAdmin && !room.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_FORBIDDEN);
        }

        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<SupportMessage> page = supportMessageRepository.findMessagesWithCursor(
                supportRoomId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = page.size() > size;
        List<SupportMessageResponse> content = page.stream().limit(size).map(SupportMessageResponse::from).toList();
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).messageId()) : null;

        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }

    // ADMIN 전체 상담방 목록 cursor 페이징 — status 필터 선택, userNickname + lastMessage 포함
    public CursorPageResponse<AdminSupportRoomResponse> getAllRooms(String cursor, int size, SupportRoomStatus status) {
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<SupportRoom> page = supportRoomRepository.findAllRoomsWithCursor(
                status, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = page.size() > size;
        List<SupportRoom> rooms = page.stream().limit(size).toList();

        List<AdminSupportRoomResponse> content = rooms.stream().map(room -> {
            String nickname = accountRepository.findById(room.getUserId())
                    .map(a -> a.getNickname())
                    .orElse(null);
            LastMessage lastMessage = supportMessageRepository
                    .findTopBySupportRoomIdOrderBySentAtDesc(room.getId())
                    .map(m -> new LastMessage(m.getContent(), m.getSentAt()))
                    .orElse(null);
            return AdminSupportRoomResponse.of(room, nickname, lastMessage);
        }).toList();

        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).supportRoomId()) : null;

        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }
}
