package com.chunbaetour.domain.community.free.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.common.CursorPage;
import com.chunbaetour.domain.community.common.CursorUtils;
import com.chunbaetour.domain.community.free.dto.FreePostCreateRequest;
import com.chunbaetour.domain.community.free.dto.FreePostGetOneResponse;
import com.chunbaetour.domain.community.free.dto.FreePostGetListResponse;
import com.chunbaetour.domain.community.free.dto.FreePostUpdateRequest;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FreePostService {

    private final FreePostRepository postRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public FreePostGetOneResponse create(Long authorId, FreePostCreateRequest request) {
        Account author = findAccount(authorId);
        FreePost post = FreePost.create(authorId, request.title(), request.content(), request.imageUrls());
        return FreePostGetOneResponse.of(postRepository.save(post), author);
    }

    @Transactional(readOnly = true)
    public FreePostGetOneResponse findById(Long postId) {
        FreePost post = findActivePost(postId);
        return FreePostGetOneResponse.of(post, findAccount(post.getAuthorId()));
    }

    @Transactional(readOnly = true)
    public CursorPage<FreePostGetListResponse> findAll(String cursor, int size) {
        Long cursorId = cursor != null ? CursorUtils.decode(cursor) : null;
        List<FreePost> posts = postRepository.findByCursor(FreePostStatus.ACTIVE, cursorId, size + 1);

        boolean hasNext = posts.size() > size;
        List<FreePost> content = hasNext ? posts.subList(0, size) : posts;

        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).getId())
                : null;

        List<FreePostGetListResponse> items = content.stream()
                .map(post -> FreePostGetListResponse.of(post, findAccount(post.getAuthorId())))
                .toList();

        return new CursorPage<>(items, nextCursor, hasNext, content.size());
    }

    @Transactional
    public FreePostGetOneResponse update(Long accountId, Long postId, FreePostUpdateRequest request) {
        FreePost post = findActivePost(postId);
        if (!post.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.COMMUNITY_002);
        }
        post.update(request.title(), request.content(), request.imageUrls());
        return FreePostGetOneResponse.of(post, findAccount(accountId));
    }

    @Transactional
    public void delete(Long accountId, Long postId) {
        FreePost post = findActivePost(postId);
        if (!post.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.COMMUNITY_003);
        }
        post.delete();
    }

    private FreePost findActivePost(Long postId) {
        return postRepository.findById(postId)
                .filter(p -> p.getStatus() == FreePostStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUNITY_001));
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUNITY_001));
    }
}
