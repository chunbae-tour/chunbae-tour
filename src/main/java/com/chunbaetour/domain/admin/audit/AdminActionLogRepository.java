package com.chunbaetour.domain.admin.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AdminActionLog 영속 저장소 (KAN-179, Admin Epic KAN-177 S01).
 *
 * <p>본 슬라이스는 write only — {@link JpaRepository#save(Object)}만 사용한다. 조회 endpoint는 별도 슬라이스가
 * 도입할 때 본 인터페이스에 검색/페이징 메서드를 추가한다(현 시점에 미리 두면 unused / YAGNI).
 */
public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {
}
