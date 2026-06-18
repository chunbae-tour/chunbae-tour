package com.chunbaetour.domain.community.companion.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CompanionPostTest {

    private static final LocalDate MEETING_DATE = LocalDate.of(2026, 8, 1);

    private CompanionPost active() {
        return CompanionPost.create(
                1L, "제목", "내용", CompanionTargetType.PLACE, 100L, "장소명", "서울", MEETING_DATE, 4);
    }

    // ── create 불변식 ───────────────────────────────────────────────────────

    @Test
    void create_maxMembers_2미만이면_INVALID_INPUT_VALUE() {
        assertThatThrownBy(() -> CompanionPost.create(
                1L, "제목", "내용", CompanionTargetType.PLACE, 100L, "장소명", "서울", MEETING_DATE, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void create_성공시_currentMembers_1_ACTIVE() {
        CompanionPost post = active();

        assertThat(post.getCurrentMembers()).isEqualTo(1);
        assertThat(post.getStatus()).isEqualTo(CompanionPostStatus.ACTIVE);
        assertThat(post.getMaxMembers()).isEqualTo(4);
        assertThat(post.getTargetType()).isEqualTo(CompanionTargetType.PLACE);
        assertThat(post.getTargetId()).isEqualTo(100L);
        assertThat(post.getTargetName()).isEqualTo("장소명");
    }

    @Test
    void create_maxMembers_2면_성공_하한경계() {
        CompanionPost post = CompanionPost.create(
                1L, "제목", "내용", CompanionTargetType.FESTIVAL, 100L, "축제명", "서울", MEETING_DATE, 2);

        assertThat(post.getMaxMembers()).isEqualTo(2);
        assertThat(post.getTargetType()).isEqualTo(CompanionTargetType.FESTIVAL);
    }

    // ── update 불변식 ───────────────────────────────────────────────────────

    @Test
    void update_target_일부만_보내면_INVALID_REQUEST() {
        CompanionPost post = active();

        // targetId만 → 부분 입력
        assertThatThrownBy(() -> post.update(null, null, null, 200L, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void update_targetType만_보내면_INVALID_REQUEST() {
        CompanionPost post = active();

        assertThatThrownBy(() -> post.update(
                null, null, CompanionTargetType.MARKET, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void update_maxMembers가_currentMembers미만이면_INVALID_INPUT_VALUE() {
        CompanionPost post = active(); // currentMembers = 1

        assertThatThrownBy(() -> post.update(null, null, null, null, null, null, null, 0))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void update_maxMembers_2미만이면_INVALID_INPUT_VALUE_하한() {
        CompanionPost post = active(); // currentMembers = 1

        // maxMembers=1은 currentMembers(1) 미만은 아니지만 하한(2) 위반
        assertThatThrownBy(() -> post.update(null, null, null, null, null, null, null, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void update_maxMembers가_currentMembers와_같으면_성공_경계() {
        CompanionPost post = active();
        ReflectionTestUtils.setField(post, "currentMembers", 3); // 참여자 누적 가정

        post.update(null, null, null, null, null, null, null, 3); // == currentMembers, >= 2

        assertThat(post.getMaxMembers()).isEqualTo(3);
    }

    @Test
    void update_maxMembers가_currentMembers미만이면_INVALID_INPUT_VALUE_경계() {
        CompanionPost post = active();
        ReflectionTestUtils.setField(post, "currentMembers", 3);

        // maxMembers=2는 하한(2) 통과하지만 currentMembers(3) 미만
        assertThatThrownBy(() -> post.update(null, null, null, null, null, null, null, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void update_target_3종_셋이면_성공() {
        CompanionPost post = active();

        post.update("새제목", null, CompanionTargetType.MARKET, 200L, "새시장", null, null, null);

        assertThat(post.getTitle()).isEqualTo("새제목");
        assertThat(post.getTargetType()).isEqualTo(CompanionTargetType.MARKET);
        assertThat(post.getTargetId()).isEqualTo(200L);
        assertThat(post.getTargetName()).isEqualTo("새시장");
    }

    @Test
    void update_부분_필드만_갱신_나머지_유지() {
        CompanionPost post = active();

        post.update("새제목", null, null, null, null, null, null, null);

        assertThat(post.getTitle()).isEqualTo("새제목");
        assertThat(post.getContent()).isEqualTo("내용");   // 유지
        assertThat(post.getMaxMembers()).isEqualTo(4);      // 유지
        assertThat(post.getTargetId()).isEqualTo(100L);     // 대상 유지
    }
}
