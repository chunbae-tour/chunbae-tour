package com.chunbaetour.domain.community.comment.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.comment.dto.CommentCreateRequest;
import com.chunbaetour.domain.community.comment.dto.CommentCreateResponse;
import com.chunbaetour.domain.community.comment.dto.CommentGetListResponse;
import com.chunbaetour.domain.community.comment.dto.CommentUpdateRequest;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.comment.repository.CommentRepository.ReplyCount;
import com.chunbaetour.domain.community.common.service.PostQueryService;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import java.util.List;
import java.util.Map;
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
public class CommentService {

    private final CommentRepository commentRepository;
    private final AccountRepository accountRepository;
    private final PostQueryService postQueryService;

    @Transactional
    public CommentCreateResponse create(Long authorId, Long postId, PostType postType, CommentCreateRequest request) {
        postQueryService.validateCommentable(postId, postType);
        Account author = findAccount(authorId);
        if (request.parentCommentId() != null) {
            Comment parent = findComment(request.parentCommentId());
            validateCommentScope(parent, postId, postType);
            if (parent.getStatus() == CommentStatus.DELETED) {
                throw new BusinessException(ErrorCode.COMMENT_ALREADY_DELETED);
            }
            if (parent.getParentCommentId() != null) {
                throw new BusinessException(ErrorCode.COMMENT_REPLY_DEPTH_EXCEEDED);
            }
        }
        Comment comment = Comment.create(postId, postType, authorId, request.content(), request.parentCommentId());
        return CommentCreateResponse.of(commentRepository.save(comment), author);
    }

    // 루트 댓글 cursor 페이징 — 삭제된 루트 댓글은 대댓글이 있는 경우만 placeholder로 포함
    public CursorPageResponse<CommentGetListResponse> findAll(Long postId, PostType postType, String cursor, int size) {
        postQueryService.validateExists(postId, postType);
        Long cursorId = decodeCursor(cursor);

        List<Comment> roots = commentRepository.findRootComments(
                postId, postType, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = roots.size() > size;
        List<Comment> content = hasNext ? roots.subList(0, size) : roots;
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        // 삭제된 루트 댓글은 작성자 조회 불필요
        Set<Long> authorIds = content.stream()
                .filter(c -> c.getStatus() != CommentStatus.DELETED)
                .map(Comment::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, Account> authors = authorIds.isEmpty() ? Map.of() :
                accountRepository.findAllById(authorIds).stream()
                        .collect(Collectors.toMap(Account::getId, Function.identity()));

        // 대댓글 수 일괄 집계 — N+1 방지
        List<Long> rootIds = content.stream().map(Comment::getId).toList();
        Map<Long, Long> replyCountMap = rootIds.isEmpty() ? Map.of() :
                commentRepository.countRepliesByParentIds(rootIds).stream()
                        .collect(Collectors.toMap(ReplyCount::getParentCommentId, ReplyCount::getCount));

        List<CommentGetListResponse> items = content.stream()
                .map(c -> CommentGetListResponse.ofRoot(
                        c,
                        authors.get(c.getAuthorId()),
                        replyCountMap.getOrDefault(c.getId(), 0L)))
                .toList();

        // 페이지별 보강(작성자·대댓글수)이라 조립형 팩토리 — size echo 통일(items.size() 아님, KAN-325)
        return CursorPageResponse.ofAssembled(items, nextCursor, hasNext, size);
    }

    // 특정 루트 댓글의 대댓글 전체 조회 (더보기)
    public List<CommentGetListResponse> findReplies(Long postId, PostType postType, Long commentId) {
        postQueryService.validateExists(postId, postType);
        Comment parent = findComment(commentId);
        validateCommentScope(parent, postId, postType);
        if (parent.getParentCommentId() != null) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        List<Comment> replies = commentRepository.findByParentCommentIdAndStatusOrderByIdAsc(
                commentId, CommentStatus.ACTIVE);

        Set<Long> authorIds = replies.stream().map(Comment::getAuthorId).collect(Collectors.toSet());
        Map<Long, Account> authors = accountRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        return replies.stream()
                .map(r -> CommentGetListResponse.ofReply(r, authors.get(r.getAuthorId())))
                .toList();
    }

    @Transactional
    public void update(Long accountId, Long postId, PostType postType, Long commentId, CommentUpdateRequest request) {
        Comment comment = findComment(commentId);
        validateCommentScope(comment, postId, postType);
        if (!comment.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.COMMENT_FORBIDDEN);
        }
        if (comment.getStatus() == CommentStatus.DELETED) {
            throw new BusinessException(ErrorCode.COMMENT_ALREADY_DELETED);
        }
        comment.update(request.content());
    }

    // 작성자 본인만 삭제 가능 — 관리자 직접 삭제는 ReportService.applyContentAction에서 처리
    @Transactional
    public void delete(Long accountId, Long postId, PostType postType, Long commentId) {
        Comment comment = findComment(commentId);
        validateCommentScope(comment, postId, postType);
        if (!comment.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.COMMENT_FORBIDDEN);
        }
        if (comment.getStatus() == CommentStatus.DELETED) {
            throw new BusinessException(ErrorCode.COMMENT_ALREADY_DELETED);
        }
        comment.delete();
    }

    // URL 경로의 postId·postType과 댓글 실제 소속 불일치 차단 — 크로스 게시글 접근 방지
    private void validateCommentScope(Comment comment, Long postId, PostType postType) {
        if (!comment.getPostId().equals(postId) || comment.getPostType() != postType) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private Long decodeCursor(String cursor) {
        if (cursor == null) return null;
        try {
            return CursorUtils.decode(cursor);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
