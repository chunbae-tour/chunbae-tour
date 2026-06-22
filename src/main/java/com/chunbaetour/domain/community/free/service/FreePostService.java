package com.chunbaetour.domain.community.free.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.community.comment.service.CommentCountService;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.community.free.dto.FreePostCreateRequest;
import com.chunbaetour.domain.community.free.dto.FreePostGetOneResponse;
import com.chunbaetour.domain.community.free.dto.FreePostGetListResponse;
import com.chunbaetour.domain.community.free.dto.FreePostUpdateRequest;
import com.chunbaetour.domain.community.common.event.PostDeletedEvent;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FreePostService {

    private final FreePostRepository postRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CommentCountService commentCountService;
    private final PostImageService postImageService;

    @Transactional
    public FreePostGetOneResponse create(Long authorId, FreePostCreateRequest request) {
        Account author = findAccount(authorId);
        // 이미지 키 영속화 전 검증 — 개수(≤5) + 본인 소유 prefix(IDOR 차단). 키는 URL이 아니라 S3 객체 키다(KAN-317).
        postImageService.validateOwnedKeys(authorId, request.imageUrls());
        FreePost post = FreePost.create(authorId, request.title(), request.content(), request.imageUrls());
        FreePost saved = postRepository.save(post);
        // 응답은 저장된 키를 presigned URL로 변환해 내려준다(비공개 버킷)
        return FreePostGetOneResponse.of(saved, author, 0L, 0L, postImageService.presignAll(saved.getImageUrls()));
    }

    // 상세 조회마다 조회수 +1 — 벌크 UPDATE라 쓰기 트랜잭션 필요. 동기 증가 방식(트래픽 적은 커뮤니티 기준).
    @Transactional
    public FreePostGetOneResponse findById(Long postId) {
        FreePost post = findActivePostWithImages(postId);
        postRepository.incrementViewCount(postId);
        Account author = accountRepository.findById(post.getAuthorId()).orElse(null);
        long commentCount = commentCountService.countByPost(postId, PostType.FREE);
        // 엔티티는 벌크 UPDATE로 동기화되지 않으므로 이번 조회분(+1)을 응답에 반영
        return FreePostGetOneResponse.of(post, author, post.getViewCount() + 1, commentCount,
                postImageService.presignAll(post.getImageUrls()));
    }

    public CursorPageResponse<FreePostGetListResponse> findAll(String cursor, int size) {
        if (size < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<FreePost> posts = postRepository.findByCursor(FreePostStatus.ACTIVE, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = posts.size() > size;
        List<FreePost> content = hasNext ? posts.subList(0, size) : posts;

        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).getId())
                : null;

        Set<Long> authorIds = content.stream().map(FreePost::getAuthorId).collect(Collectors.toSet());
        Map<Long, Account> authors = accountRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<Long> postIds = content.stream().map(FreePost::getId).toList();
        Map<Long, Long> commentCounts = commentCountService.countByPosts(postIds, PostType.FREE);

        List<FreePostGetListResponse> items = content.stream()
                .map(post -> FreePostGetListResponse.of(
                        post, authors.get(post.getAuthorId()), commentCounts.getOrDefault(post.getId(), 0L),
                        postImageService.presignAll(post.getImageUrls())))
                .toList();

        // 페이지별 보강(작성자·댓글수·이미지 presign)이라 조립형 팩토리 — size echo 통일(content.size() 아님, KAN-325)
        return CursorPageResponse.ofAssembled(items, nextCursor, hasNext, size);
    }

    @Transactional
    public FreePostGetOneResponse update(Long accountId, Long postId, FreePostUpdateRequest request) {
        FreePost post = findActivePostWithImages(postId);
        if (!post.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.POST_UPDATE_FORBIDDEN);
        }
        // 이미지 키 영속화 전 검증 — 개수(≤5) + 본인 소유 prefix(IDOR 차단)
        postImageService.validateOwnedKeys(accountId, request.imageUrls());
        // 이미지가 교체되는 경우(request.imageUrls != null), 새 목록서 빠진 옛 키는 고아가 되므로 커밋 후 정리
        if (request.imageUrls() != null) {
            List<String> removed = new ArrayList<>(post.getImageUrls());
            removed.removeAll(request.imageUrls());
            postImageService.deleteAllAfterCommit(removed);
        }
        post.update(request.title(), request.content(), request.imageUrls());
        long commentCount = commentCountService.countByPost(postId, PostType.FREE);
        return FreePostGetOneResponse.of(post, findAccount(accountId), post.getViewCount(), commentCount,
                postImageService.presignAll(post.getImageUrls()));
    }

    @Transactional
    public void delete(Long accountId, Long postId) {
        // 이미지 키를 함께 로드 — 삭제 후 S3 객체 정리에 필요(soft-delete라 free_post_images 행은 남지만 객체는 회수)
        FreePost post = findActivePostWithImages(postId);
        if (!post.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.POST_DELETE_FORBIDDEN);
        }
        // 글의 이미지 객체는 커밋 후 best-effort 삭제 — 삭제된 글은 다시 노출되지 않으므로 S3 객체를 회수한다(잔존분은 스케줄러 회수)
        postImageService.deleteAllAfterCommit(new ArrayList<>(post.getImageUrls()));
        post.delete();
        eventPublisher.publishEvent(new PostDeletedEvent(post.getId(), PostType.FREE));
    }

    private FreePost findActivePost(Long postId) {
        return postRepository.findById(postId)
                .filter(p -> p.getStatus() == FreePostStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private FreePost findActivePostWithImages(Long postId) {
        return postRepository.findByIdWithImages(postId)
                .filter(p -> p.getStatus() == FreePostStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

}
