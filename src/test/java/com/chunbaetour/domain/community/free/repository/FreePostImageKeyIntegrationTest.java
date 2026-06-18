package com.chunbaetour.domain.community.free.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * FreePostRepository.existsByImageKey 통합 테스트 (KAN-317).
 * free_post_images(@ElementCollection)에 대한 native EXISTS 쿼리가 실제 스키마에서 동작하는지 검증.
 */
@SpringBootTest
class FreePostImageKeyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private FreePostRepository freePostRepository;

    @AfterEach
    void cleanup() {
        freePostRepository.deleteAll();
    }

    @Test
    @DisplayName("existsByImageKey — 참조된 키는 true, 미참조 키는 false")
    void existsByImageKey() {
        freePostRepository.save(FreePost.create(
                1L, "제목", "내용", List.of("posts/free/1/a.jpg", "posts/free/1/b.jpg")));

        assertThat(freePostRepository.existsByImageKey("posts/free/1/a.jpg")).isTrue();
        assertThat(freePostRepository.existsByImageKey("posts/free/1/b.jpg")).isTrue();
        assertThat(freePostRepository.existsByImageKey("posts/free/1/orphan.jpg")).isFalse();
    }

    @Test
    @DisplayName("existsByImageKey — DELETED 글의 키는 false(회수 대상), HIDDEN 글의 키는 true(복원 대비 유지)")
    void existsByImageKey_excludesDeletedButKeepsHidden() {
        FreePost deleted = FreePost.create(1L, "삭제글", "내용", List.of("posts/free/1/del.jpg"));
        deleted.delete();
        freePostRepository.save(deleted);

        FreePost hidden = FreePost.create(1L, "숨김글", "내용", List.of("posts/free/1/hid.jpg"));
        hidden.hide();
        freePostRepository.save(hidden);

        // DELETED는 복원 불가 → 참조에서 제외(고아 회수 가능)
        assertThat(freePostRepository.existsByImageKey("posts/free/1/del.jpg")).isFalse();
        // HIDDEN은 복원 가능 → 참조 유지
        assertThat(freePostRepository.existsByImageKey("posts/free/1/hid.jpg")).isTrue();
    }
}
