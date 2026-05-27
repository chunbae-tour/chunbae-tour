package com.chunbaetour.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.notification.entity.Notification;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class NotificationRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    // soft-deleted 알림도 직접 삭제해야 하므로 JdbcTemplate 사용 (@SQLRestriction 우회)
    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM notifications");
    }

    // userId가 다른 알림은 조회되지 않음
    @Test
    void findWithCursor_excludesOtherUsersNotifications() {
        notificationRepository.saveAll(List.of(
                buildNotification(USER_A),
                buildNotification(USER_B)
        ));

        List<Notification> result = notificationRepository.findWithCursor(USER_A, null, PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(USER_A);
    }

    // soft-deleted 알림은 @SQLRestriction("deleted_at IS NULL")으로 조회에서 제외됨
    @Test
    void findWithCursor_excludesSoftDeletedNotifications() {
        Notification active = notificationRepository.save(buildNotification(USER_A));
        Notification deleted = notificationRepository.save(buildNotification(USER_A));
        deleted.delete();
        notificationRepository.save(deleted);

        List<Notification> result = notificationRepository.findWithCursor(USER_A, null, PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(active.getId());
    }

    // id DESC 순서로 반환됨
    @Test
    void findWithCursor_returnsIdDescOrder() {
        notificationRepository.saveAll(List.of(
                buildNotification(USER_A),
                buildNotification(USER_A),
                buildNotification(USER_A)
        ));

        List<Notification> result = notificationRepository.findWithCursor(USER_A, null, PageRequest.of(0, 10));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isGreaterThan(result.get(1).getId());
        assertThat(result.get(1).getId()).isGreaterThan(result.get(2).getId());
    }

    // cursorId 미만 id만 반환됨 — 커서 이후 페이지 조회 정확성 검증
    @Test
    void findWithCursor_returnsOnlyIdsBelowCursor() {
        List<Notification> saved = notificationRepository.saveAll(List.of(
                buildNotification(USER_A),
                buildNotification(USER_A),
                buildNotification(USER_A)
        ));
        Long cursorId = saved.get(1).getId(); // 3개 중 중간 id를 cursor로 설정

        List<Notification> result = notificationRepository.findWithCursor(USER_A, cursorId, PageRequest.of(0, 10));

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(n -> n.getId() < cursorId);
    }

    // size+1 조회 시 Pageable 제한이 정확히 적용됨 — 서비스의 hasNext 계산 전제 조건
    @Test
    void findWithCursor_respectsPageableLimit() {
        for (int i = 0; i < 5; i++) {
            notificationRepository.save(buildNotification(USER_A));
        }

        List<Notification> result = notificationRepository.findWithCursor(USER_A, null, PageRequest.of(0, 3));

        assertThat(result).hasSize(3);
    }

    private Notification buildNotification(Long userId) {
        return Notification.builder()
                .userId(userId)
                .type(NotificationType.CHAT_JOIN_REQUEST)
                .title("테스트 알림")
                .message("테스트 메시지입니다.")
                .referenceType(NotificationReferenceType.JOIN_REQUEST)
                .referenceId(1L)
                .build();
    }
}
