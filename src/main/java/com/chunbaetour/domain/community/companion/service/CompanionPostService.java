package com.chunbaetour.domain.community.companion.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.common.CursorPage;
import com.chunbaetour.domain.community.common.CursorUtils;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateRequest;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetListResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetOneResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateRequest;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import java.time.LocalDate;
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
public class CompanionPostService {

    private final CompanionPostRepository postRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public CompanionPostCreateResponse create(Long authorId, CompanionPostCreateRequest request) {
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

    @Transactional(readOnly = true)
    public CompanionPostGetOneResponse findById(Long postId) {
        CompanionPost post = findActivePost(postId);
        Account author = findAccount(post.getAuthorId());
        return CompanionPostGetOneResponse.of(post, author);
    }

    @Transactional(readOnly = true)
    public CursorPage<CompanionPostGetListResponse> findAll(
            String region, LocalDate meetingDate, String cursor, int size) {
        Long cursorId = cursor != null ? CursorUtils.decode(cursor) : null;
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

        List<CompanionPostGetListResponse> items = content.stream()
                .map(post -> CompanionPostGetListResponse.of(post, authors.get(post.getAuthorId())))
                .toList();

        return new CursorPage<>(items, nextCursor, hasNext, content.size());
    }

    @Transactional
    public CompanionPostCreateResponse update(Long accountId, Long postId, CompanionPostUpdateRequest request) {
        CompanionPost post = findActivePost(postId);
        if (!post.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.COMMUNITY_002);
        }
        post.update(
                request.title(), request.content(),
                request.placeId(), request.placeName(),
                request.region(), request.meetingDate(),
                request.maxMembers() != null ? request.maxMembers() : post.getMaxMembers()
        );
        return CompanionPostCreateResponse.of(post, findAccount(accountId));
    }

    @Transactional
    public void delete(Long accountId, Long postId) {
        CompanionPost post = findActivePost(postId);
        if (!post.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.COMMUNITY_003);
        }
        post.delete();
    }

    private CompanionPost findActivePost(Long postId) {
        return postRepository.findById(postId)
                .filter(p -> p.getStatus() != CompanionPostStatus.DELETED
                        && p.getStatus() != CompanionPostStatus.HIDDEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUNITY_001));
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUNITY_001));
    }
}
