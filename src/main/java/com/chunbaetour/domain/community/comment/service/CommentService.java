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

    public CursorPageResponse<CommentGetListResponse> findAll(Long postId, PostType postType, String cursor, int size) {
        postQueryService.validateExists(postId, postType);
        Long cursorId = decodeCursor(cursor);
        // size + 1개 조회: 별도 COUNT 쿼리 없이 다음 페이지 존재 여부를 판단하기 위함
        List<Comment> comments = commentRepository.findByPost(
                postId, postType, CommentStatus.ACTIVE, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = comments.size() > size;
        List<Comment> content = hasNext ? comments.subList(0, size) : comments;

        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).getId())
                : null;

        // authorId를 Set으로 모아 한 번에 조회 — 댓글마다 개별 조회 시 N+1 발생
        Set<Long> authorIds = content.stream().map(Comment::getAuthorId).collect(Collectors.toSet());
        Map<Long, Account> authors = accountRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<CommentGetListResponse> items = content.stream()
                .map(c -> CommentGetListResponse.of(c, authors.get(c.getAuthorId())))
                .toList();

        return new CursorPageResponse<>(items, nextCursor, hasNext, content.size());
    }

    @Transactional
    public void update(Long accountId, Long commentId, CommentUpdateRequest request) {
        Comment comment = findComment(commentId);
        if (comment.getStatus() == CommentStatus.DELETED) {
            throw new BusinessException(ErrorCode.COMMENT_ALREADY_DELETED);
        }
        if (!comment.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.COMMENT_FORBIDDEN);
        }
        comment.update(request.content());
    }

    @Transactional
    public void delete(Long accountId, Long commentId) {
        Comment comment = findComment(commentId);
        if (!comment.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.COMMENT_FORBIDDEN);
        }
        if (comment.getStatus() == CommentStatus.DELETED) {
            throw new BusinessException(ErrorCode.COMMENT_ALREADY_DELETED);
        }
        comment.delete();
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
