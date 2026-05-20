package com.chunbaetour.domain.community.free;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.common.CursorPage;
import com.chunbaetour.domain.community.free.dto.FreePostCreateRequest;
import com.chunbaetour.domain.community.free.dto.FreePostGetListResponse;
import com.chunbaetour.domain.community.free.dto.FreePostGetOneResponse;
import com.chunbaetour.domain.community.free.dto.FreePostUpdateRequest;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import com.chunbaetour.domain.community.free.service.FreePostService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FreePostServiceTest {

    @Mock
    private FreePostRepository postRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private FreePostService postService;

    private Account author;
    private FreePost activePost;

    @BeforeEach
    void setUp() {
        author = Account.registerUser("test@example.com", "hashed", "춘배여행자");

        activePost = FreePost.create(1L, "제주도 여행 후기", "성산일출봉 강추!", List.of());
    }

    @Test
    void 소유자가_아닌_유저가_수정하면_403() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));

        assertThatThrownBy(() -> postService.update(999L, 1L, new FreePostUpdateRequest("제목", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_002);
    }

    @Test
    void 소유자가_아닌_유저가_삭제하면_403() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));

        assertThatThrownBy(() -> postService.delete(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_003);
    }

    @Test
    void 삭제된_게시글_조회하면_404() {
        FreePost deletedPost = FreePost.create(1L, "삭제글", "내용", List.of());
        deletedPost.delete();
        given(postRepository.findById(1L)).willReturn(Optional.of(deletedPost));

        assertThatThrownBy(() -> postService.findById(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_001);
    }

    @Test
    void 커서없이_목록조회하면_전체_반환() {
        FreePost post1 = FreePost.create(1L, "글1", "내용1", List.of());
        FreePost post2 = FreePost.create(1L, "글2", "내용2", List.of());
        given(postRepository.findByCursor(FreePostStatus.ACTIVE, null, Pageable.ofSize(11)))
                .willReturn(List.of(post1, post2));
        given(accountRepository.findAllById(any())).willReturn(List.of(author));

        CursorPage<FreePostGetListResponse> result = postService.findAll(null, 10);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void size보다_많은_결과면_hasNext_true() {
        List<FreePost> posts = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(i -> FreePost.create(1L, "글" + i, "내용", List.of()))
                .toList();
        given(postRepository.findByCursor(FreePostStatus.ACTIVE, null, Pageable.ofSize(11)))
                .willReturn(posts);
        given(accountRepository.findAllById(any())).willReturn(List.of(author));

        CursorPage<FreePostGetListResponse> result = postService.findAll(null, 10);

        assertThat(result.content()).hasSize(10);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }
}
