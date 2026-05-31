-- =====================================================================
-- V2 — admin_action_logs (KAN-179, Admin Epic KAN-177 S01)
-- =====================================================================
-- 운영 액션 추적용 append-only 테이블. AdminActionLog entity와 1:1 매핑.
--
-- 필드 책임
--   admin_user_id: 액션 수행자 (운영자 userId)
--   action_type:   액션 enum (예: USER_SUSPEND / USER_UNSUSPEND) — VARCHAR(64) + JPA @Enumerated(STRING)
--   target_type:   대상 도메인 enum (USER/MERCHANT/SHOP/... 13개)
--   target_id:     대상 entity PK
--   reason:        운영자 입력 사유 (nullable — 본 슬라이스는 항상 null, 후속 슬라이스에서 보강)
--   before_status: 변경 전 상태 (예: ACTIVE)
--   after_status:  변경 후 상태 (예: SUSPENDED)
--   created_at:    JPA @CreatedDate
--
-- 인덱스 4개
--   admin_user_id: 운영자별 액션 이력 조회 (CS 응대 + 권한 남용 추적)
--   target(type+id): 특정 대상에 가해진 모든 액션 조회 (분쟁 대응)
--   action_type: 액션 종류별 통계 (예: 정지 누적 추이)
--   created_at: 시간순 정렬 + 기간 필터 (감사 조회)
--
-- 정책
--   본 슬라이스는 단순 동기 write. 마스킹/비동기/샘플링은 후속 슬라이스.
--   조회 endpoint도 별도 슬라이스 — 본 V2는 write 전용 schema.
-- =====================================================================

CREATE TABLE admin_action_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    before_status VARCHAR(64) NULL,
    after_status VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_admin_action_logs_admin_user_id (admin_user_id),
    INDEX idx_admin_action_logs_target (target_type, target_id),
    INDEX idx_admin_action_logs_action_type (action_type),
    INDEX idx_admin_action_logs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
