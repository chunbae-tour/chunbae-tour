package com.chunbaetour.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.chunbaetour.domain.notification.entity.Notification;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.chunbaetour.domain.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class NotificationRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    // soft-deleted 알림도 직접 삭제해야 하므로 JdbcTemplate 사용 (@SQLRestriction 우회)
    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM notifications");
    }

    // ===== findWithCursor =====

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

    // cursorId 미만 id만 반환됨
    @Test
    void findWithCursor_returnsOnlyIdsBelowCursor() {
        List<Notification> saved = notificationRepository.saveAll(List.of(
                buildNotification(USER_A),
                buildNotification(USER_A),
                buildNotification(USER_A)
        ));
        Long cursorId = saved.get(1).getId();

        List<Notification> result = notificationRepository.findWithCursor(USER_A, cursorId, PageRequest.of(0, 10));

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(n -> n.getId() < cursorId);
    }

    // Pageable size 제한 적용 — size+1 요청으로 hasNext 계산 가능
    @Test
    void findWithCursor_respectsPageableLimit() {
        for (int i = 0; i < 5; i++) {
            notificationRepository.save(buildNotification(USER_A));
        }

        List<Notification> result = notificationRepository.findWithCursor(USER_A, null, PageRequest.of(0, 3));

        assertThat(result).hasSize(3);
    }

    // ===== findByIdAndUserId =====

    // 타 userId의 알림은 반환하지 않음 — 소유권 검증
    @Test
    void findByIdAndUserId_returnsEmptyForOtherUserId() {
        Notification saved = notificationRepository.save(buildNotification(USER_A));

        Optional<Notification> result = notificationRepository.findByIdAndUserId(saved.getId(), USER_B);

        assertThat(result).isEmpty();
    }

    // soft-deleted 알림은 단건 조회 대상 아님 — @SQLRestriction 적용
    @Test
    void findByIdAndUserId_returnsEmptyForSoftDeletedNotification() {
        Notification saved = notificationRepository.save(buildNotification(USER_A));
        saved.delete();
        notificationRepository.save(saved);

        Optional<Notification> result = notificationRepository.findByIdAndUserId(saved.getId(), USER_A);

        assertThat(result).isEmpty();
    }

    // ===== findByIdAndUserIdIncludingDeleted =====

    // soft-deleted 알림도 조회됨 — 멱등 삭제 시 소유권 확인 용도
    @Test
    void findByIdAndUserIdIncludingDeleted_returnsSoftDeletedNotification() {
        Notification saved = notificationRepository.save(buildNotification(USER_A));
        saved.delete();
        notificationRepository.save(saved);

        Optional<Notification> result = notificationRepository.findByIdAndUserIdIncludingDeleted(saved.getId(), USER_A);

        assertThat(result).isPresent();
        assertThat(result.get().getDeletedAt()).isNotNull();
    }

    // 타 userId 알림은 반환하지 않음 — 소유권 검증 유지
    @Test
    void findByIdAndUserIdIncludingDeleted_returnsEmptyForOtherUser() {
        Notification saved = notificationRepository.save(buildNotification(USER_A));

        Optional<Notification> result = notificationRepository.findByIdAndUserIdIncludingDeleted(saved.getId(), USER_B);

        assertThat(result).isEmpty();
    }

    // ===== deleteNotification 서비스 통합 =====

    // deleteNotification 호출 후 dirty checking으로 DB에 deleted_at 반영되고, 목록 조회에서 제외됨
    @Test
    void deleteNotification_persistsSoftDeleteToDb() {
        Notification saved = notificationRepository.save(buildNotification(USER_A));

        notificationService.deleteNotification(USER_A, saved.getId());

        Integer deletedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class, saved.getId());
        assertThat(deletedCount).isEqualTo(1);

        List<Notification> visible = notificationRepository.findWithCursor(USER_A, null, PageRequest.of(0, 10));
        assertThat(visible).noneMatch(n -> n.getId().equals(saved.getId()));
    }

    // 이미 soft-deleted된 알림에 deleteNotification 재호출 — 예외 없음 (멱등 204)
    @Test
    void deleteNotification_alreadyDeleted_isIdempotent() {
        Notification saved = notificationRepository.save(buildNotification(USER_A));
        notificationService.deleteNotification(USER_A, saved.getId());

        assertThatNoException().isThrownBy(() ->
                notificationService.deleteNotification(USER_A, saved.getId()));
    }

    // ===== markAllAsRead =====

    // 요청 userId의 unread 알림만 isRead=true로 변경됨
    @Test
    void markAllAsRead_updatesOnlyUnreadForUserId() {
        notificationRepository.saveAll(List.of(
                buildNotification(USER_A),
                buildNotification(USER_A),
                buildNotification(USER_A)
        ));

        notificationRepository.markAllAsRead(USER_A);

        int unreadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = false AND deleted_at IS NULL",
                Integer.class, USER_A);
        assertThat(unreadCount).isZero();
    }

    // 타 userId 알림과 soft-deleted 알림은 변경되지 않음
    @Test
    void markAllAsRead_doesNotUpdateOtherUserOrDeletedNotifications() {
        Notification otherUserNotif = notificationRepository.save(buildNotification(USER_B));
        Notification deletedNotif = notificationRepository.save(buildNotification(USER_A));
        deletedNotif.delete();
        notificationRepository.save(deletedNotif);

        notificationRepository.markAllAsRead(USER_A);

        // USER_B 알림은 변경되지 않음
        int otherUserUnread = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ? AND is_read = false",
                Integer.class, otherUserNotif.getId());
        assertThat(otherUserUnread).isEqualTo(1);

        // soft-deleted 알림은 변경되지 않음
        int deletedUnread = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ? AND is_read = false",
                Integer.class, deletedNotif.getId());
        assertThat(deletedUnread).isEqualTo(1);
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
