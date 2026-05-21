package com.chunbaetour.domain.community.companion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.common.CursorPage;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetListResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateRequest;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import com.chunbaetour.domain.community.companion.service.CompanionPostService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompanionPostServiceTest {

    @Mock
    private CompanionPostRepository postRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private CompanionPostService postService;

    private Account author;
    private CompanionPost activePost;

    @BeforeEach
    void setUp() {
        author = Account.registerUser("test@example.com", "hashed", "춘배여행자");
        ReflectionTestUtils.setField(author, "id", 1L);

        activePost = CompanionPost.create(
                1L, "제주도 동행 모집", "같이 가요!", 100L, "성산일출봉",
                "제주", LocalDate.now().plusDays(7), 4);
    }

    @Test
    void 소유자가_아닌_유저가_수정하면_403() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));

        assertThatThrownBy(() -> postService.update(999L, 1L,
                new CompanionPostUpdateRequest("수정제목", null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_UPDATE_FORBIDDEN);
    }

    @Test
    void 소유자가_아닌_유저가_삭제하면_403() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));

        assertThatThrownBy(() -> postService.delete(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_DELETE_FORBIDDEN);
    }

    @Test
    void 삭제된_게시글_조회하면_404() {
        CompanionPost deleted = CompanionPost.create(
                1L, "삭제글", "내용", 100L, "장소", "서울",
                LocalDate.now().plusDays(1), 2);
        deleted.delete();
        given(postRepository.findById(1L)).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> postService.findById(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void 커서없이_목록조회하면_전체_반환() {
        CompanionPost p1 = CompanionPost.create(1L, "글1", "내용", 1L, "장소", "서울",
                LocalDate.now().plusDays(1), 2);
        CompanionPost p2 = CompanionPost.create(1L, "글2", "내용", 1L, "장소", "부산",
                LocalDate.now().plusDays(2), 3);
        given(postRepository.findByFilters(CompanionPostStatus.ACTIVE, null, null, null, Pageable.ofSize(11)))
                .willReturn(List.of(p1, p2));
        given(accountRepository.findAllById(any())).willReturn(List.of(author));

        CursorPage<CompanionPostGetListResponse> result = postService.findAll(null, null, null, 10);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void size보다_많은_결과면_hasNext_true() {
        List<CompanionPost> posts = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(i -> {
                    CompanionPost p = CompanionPost.create(1L, "글" + i, "내용", 1L, "장소", "서울",
                            LocalDate.now().plusDays(i), 2);
                    ReflectionTestUtils.setField(p, "id", (long) i);
                    return p;
                })
                .toList();
        given(postRepository.findByFilters(CompanionPostStatus.ACTIVE, null, null, null, Pageable.ofSize(11)))
                .willReturn(posts);
        given(accountRepository.findAllById(any())).willReturn(List.of(author));

        CursorPage<CompanionPostGetListResponse> result = postService.findAll(null, null, null, 10);

        assertThat(result.content()).hasSize(10);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    void placeId만_있고_placeName_없으면_400() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));

        assertThatThrownBy(() -> postService.update(1L, 1L,
                new CompanionPostUpdateRequest(null, null, 200L, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void placeName만_있고_placeId_없으면_400() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));

        assertThatThrownBy(() -> postService.update(1L, 1L,
                new CompanionPostUpdateRequest(null, null, null, "새 장소명", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
