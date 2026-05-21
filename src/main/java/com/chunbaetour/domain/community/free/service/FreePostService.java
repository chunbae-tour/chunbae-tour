package com.chunbaetour.domain.community.free.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.community.free.dto.FreePostCreateRequest;
import com.chunbaetour.domain.community.free.dto.FreePostGetOneResponse;
import com.chunbaetour.domain.community.free.dto.FreePostGetListResponse;
import com.chunbaetour.domain.community.free.dto.FreePostUpdateRequest;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
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
public class FreePostService {

    private final FreePostRepository postRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public FreePostGetOneResponse create(Long authorId, FreePostCreateRequest request) {
        Account author = findAccount(authorId);
        FreePost post = FreePost.create(authorId, request.title(), request.content(), request.imageUrls());
        return FreePostGetOneResponse.of(postRepository.save(post), author);
    }

    public FreePostGetOneResponse findById(Long postId) {
        FreePost post = findActivePost(postId);
        Account author = accountRepository.findById(post.getAuthorId()).orElse(null);
        return FreePostGetOneResponse.of(post, author);
    }

    public CursorPageResponse<FreePostGetListResponse> findAll(String cursor, int size) {
        Long cursorId = decodeCursor(cursor);
        List<FreePost> posts = postRepository.findByCursor(FreePostStatus.ACTIVE, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = posts.size() > size;
        List<FreePost> content = hasNext ? posts.subList(0, size) : posts;

        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).getId())
                : null;

        Set<Long> authorIds = content.stream().map(FreePost::getAuthorId).collect(Collectors.toSet());
        Map<Long, Account> authors = accountRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<FreePostGetListResponse> items = content.stream()
                .map(post -> FreePostGetListResponse.of(post, authors.get(post.getAuthorId())))
                .toList();

        return new CursorPageResponse<>(items, nextCursor, hasNext, content.size());
    }

    @Transactional
    public FreePostGetOneResponse update(Long accountId, Long postId, FreePostUpdateRequest request) {
        FreePost post = findActivePost(postId);
        if (!post.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.POST_UPDATE_FORBIDDEN);
        }
        post.update(request.title(), request.content(), request.imageUrls());
        return FreePostGetOneResponse.of(post, findAccount(accountId));
    }

    @Transactional
    public void delete(Long accountId, Long postId) {
        FreePost post = findActivePost(postId);
        if (!post.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.POST_DELETE_FORBIDDEN);
        }
        post.delete();
    }

    private FreePost findActivePost(Long postId) {
        return postRepository.findById(postId)
                .filter(p -> p.getStatus() == FreePostStatus.ACTIVE)
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
