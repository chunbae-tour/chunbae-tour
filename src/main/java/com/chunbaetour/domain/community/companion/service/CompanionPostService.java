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
import com.chunbaetour.domain.community.comment.service.CommentCountService;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.community.common.event.PostDeletedEvent;
import com.chunbaetour.domain.community.companion.repository.CompanionPostQueryRepository;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionPostService {

    private final CompanionPostRepository postRepository;
    private final CompanionPostQueryRepository postQueryRepository;
    private final AccountRepository accountRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CommentCountService commentCountService;
    private final CompanionTargetValidator targetValidator;

    @Transactional
    public CompanionPostCreateResponse create(Long authorId, CompanionPostCreateRequest request) {
        Account author = findAccount(authorId);
        // 대상(PLACE/MARKET/FESTIVAL)이 실제 존재하는지 검증 — 지도 좌표를 못 받는 깨진 동행글 방지
        ValidatedCompanionTarget target = targetValidator.validate(request.targetType(), request.targetId());
        CompanionPost post = CompanionPost.create(
                authorId,
                request.title(),
                request.content(),
                target.targetType(),
                target.targetId(),
                target.targetName(),
                request.region(),
                request.meetingDate(),
                request.maxMembers()
        );
        return CompanionPostCreateResponse.of(postRepository.save(post), author);
    }

    // 상세 조회마다 조회수 +1 — 벌크 UPDATE라 쓰기 트랜잭션 필요. 동기 증가 방식(트래픽 적은 커뮤니티 기준).
    @Transactional
    public CompanionPostGetOneResponse findById(Long postId) {
        CompanionPost post = findActivePost(postId);
        postRepository.incrementViewCount(postId);
        Account author = accountRepository.findById(post.getAuthorId()).orElse(null);
        Optional<ChatRoom> chatRoom = chatRoomRepository.findByPostId(post.getId());
        Long chatRoomId = chatRoom.map(ChatRoom::getId).orElse(null);
        ChatRoomStatus chatRoomStatus = chatRoom.map(ChatRoom::getStatus).orElse(null);
        long commentCount = commentCountService.countByPost(postId, PostType.COMPANION);
        // 엔티티는 벌크 UPDATE로 동기화되지 않으므로 이번 조회분(+1)을 응답에 반영
        return CompanionPostGetOneResponse.of(
                post, author, chatRoomId, chatRoomStatus, post.getViewCount() + 1, commentCount);
    }

    public CursorPageResponse<CompanionPostGetListResponse> findAll(
            String region, LocalDate meetingDate, String cursor, int size) {
        Long cursorId = decodeCursor(cursor);
        List<CompanionPost> posts = postQueryRepository.findByFilters(
                CompanionPostStatus.ACTIVE, region, meetingDate, cursorId, size + 1);

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

        Map<Long, Long> commentCounts = commentCountService.countByPosts(postIds, PostType.COMPANION);

        List<CompanionPostGetListResponse> items = content.stream()
                .map(post -> CompanionPostGetListResponse.of(
                        post, authors.get(post.getAuthorId()), chatRoomIdByPostId.get(post.getId()),
                        commentCounts.getOrDefault(post.getId(), 0L)))
                .toList();

        // 페이지별 보강(작성자·채팅방·댓글수)이라 조립형 팩토리 — size echo 통일(content.size() 아님, KAN-325)
        return CursorPageResponse.ofAssembled(items, nextCursor, hasNext, size);
    }

    @Transactional
    public CompanionPostUpdateResponse update(Long accountId, Long postId, CompanionPostUpdateRequest request) {
        CompanionPost post = findActivePost(postId);
        if (!post.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.POST_UPDATE_FORBIDDEN);
        }
        // 대상 변경 시(셋 모두 입력) 실존 검증 — 부분 입력은 엔티티 update에서 INVALID_REQUEST로 거부
        ValidatedCompanionTarget target = null;
        if (request.targetType() != null && request.targetId() != null && request.targetName() != null) {
            target = targetValidator.validate(request.targetType(), request.targetId());
        }
        // 입력 불변식(target 3종 셋, maxMembers >= currentMembers)은 엔티티 update에서 검증
        post.update(
                request.title(), request.content(),
                target != null ? target.targetType() : request.targetType(),
                target != null ? target.targetId() : request.targetId(),
                target != null ? target.targetName() : request.targetName(),
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
        eventPublisher.publishEvent(new PostDeletedEvent(post.getId(), PostType.COMPANION));
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
