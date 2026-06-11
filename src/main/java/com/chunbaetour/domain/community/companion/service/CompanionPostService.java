package com.chunbaetour.domain.community.companion.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateRequest;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetListResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetOneResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateRequest;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateResponse;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionPostService {

    private final CompanionPostRepository postRepository;
    private final AccountRepository accountRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public CompanionPostCreateResponse create(Long authorId, CompanionPostCreateRequest request) {
        if (request.maxMembers() < 2) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Account author = findAccount(authorId);
        CompanionPost post = CompanionPost.create(
                authorId,
                request.title(),
                request.content(),
                request.placeId(),
                request.placeName(),
                request.region(),
                request.meetingDate(),
                request.maxMembers()
        );
        return CompanionPostCreateResponse.of(postRepository.save(post), author);
    }

    public CompanionPostGetOneResponse findById(Long postId) {
        CompanionPost post = findActivePost(postId);
        Account author = accountRepository.findById(post.getAuthorId()).orElse(null);
        Optional<ChatRoom> chatRoom = chatRoomRepository.findByPostId(post.getId());
        Long chatRoomId = chatRoom.map(ChatRoom::getId).orElse(null);
        ChatRoomStatus chatRoomStatus = chatRoom.map(ChatRoom::getStatus).orElse(null);
        return CompanionPostGetOneResponse.of(post, author, chatRoomId, chatRoomStatus);
    }

    public CursorPageResponse<CompanionPostGetListResponse> findAll(
            String region, LocalDate meetingDate, String cursor, int size) {
        Long cursorId = decodeCursor(cursor);
        List<CompanionPost> posts = postRepository.findByFilters(
                CompanionPostStatus.ACTIVE, region, meetingDate, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = posts.size() > size;
        List<CompanionPost> content = hasNext ? posts.subList(0, size) : posts;

        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).getId())
                : null;

        Set<Long> authorIds = content.stream().map(CompanionPost::getAuthorId).collect(Collectors.toSet());
        Map<Long, Account> authors = accountRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        Set<Long> postIds = content.stream().map(CompanionPost::getId).collect(Collectors.toSet());
        Map<Long, Long> chatRoomIdByPostId = postIds.isEmpty()
                ? Map.of()
                : chatRoomRepository.findAllByPostIdIn(postIds).stream()
                        .collect(Collectors.toMap(ChatRoom::getPostId, ChatRoom::getId));

        List<CompanionPostGetListResponse> items = content.stream()
                .map(post -> CompanionPostGetListResponse.of(
                        post, authors.get(post.getAuthorId()), chatRoomIdByPostId.get(post.getId())))
                .toList();

        return new CursorPageResponse<>(items, nextCursor, hasNext, content.size());
    }

    @Transactional
    public CompanionPostUpdateResponse update(Long accountId, Long postId, CompanionPostUpdateRequest request) {
        CompanionPost post = findActivePost(postId);
        if (!post.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.POST_UPDATE_FORBIDDEN);
        }
        // placeId·placeName은 쌍으로 수정해야 함 — 한쪽만 보내면 데이터 불일치
        if ((request.placeId() == null) != (request.placeName() == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (request.maxMembers() != null && request.maxMembers() < post.getCurrentMembers()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        post.update(
                request.title(), request.content(),
                request.placeId(), request.placeName(),
                request.region(), request.meetingDate(),
                request.maxMembers()
        );
        return CompanionPostUpdateResponse.of(post, findAccount(accountId));
    }

    @Transactional
    public void delete(Long accountId, Long postId) {
        CompanionPost post = findActivePost(postId);
        if (!post.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.POST_DELETE_FORBIDDEN);
        }
        post.delete();
    }

    private CompanionPost findActivePost(Long postId) {
        // CLOSED 게시글은 목록(findAll)에서 제외되지만, 직접 링크로 단건 조회·수정·삭제는 허용
        return postRepository.findById(postId)
                .filter(p -> p.getStatus() != CompanionPostStatus.DELETED
                        && p.getStatus() != CompanionPostStatus.HIDDEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Long decodeCursor(String cursor) {
        if (cursor == null) return null;
        try {
            return CursorUtils.decode(cursor);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
