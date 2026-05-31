package com.chunbaetour.domain.admin.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AdminActionLog 영속 저장소 (KAN-179, Admin Epic KAN-177 S01).
 *
 * <p>프로덕션 경로는 save-only (append-only 감사 로그) — {@link JpaRepository#save(Object)}만 사용한다.
 * 테스트 cleanup에서만 {@link JpaRepository#deleteAll()}을 사용한다. 조회/검색 메서드는 별도 조회 슬라이스가
 * 도입할 때 추가한다(현 시점에 미리 두면 unused / YAGNI).
 */
public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {
}
