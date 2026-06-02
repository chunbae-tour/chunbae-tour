-- =====================================================================
-- V202606020456 — shop_certifications (KAN-204, Admin Epic KAN-177 S05)
-- =====================================================================
-- 상인 인증 신청 1건의 상태 + 사유 보유. ShopCertification entity와 1:1 매핑.
--
-- 필드 책임
--   shop_id:          인증 대상 가게 (shops.id)
--   status:           PENDING / APPROVED / REJECTED / CANCELLED — VARCHAR(32) + JPA @Enumerated(STRING)
--                      (CANCELLED = 운영자가 APPROVED 인증을 회수, 방향 B. cancel_reason 기록 + Shop.unmarkCertified())
--   submitted_reason: 상인이 신청 시 입력한 사유 (nullable)
--   reject_reason:    운영자 거절 사유 (nullable — 거절 시에만 기록)
--   cancel_reason:    운영자 취소 사유 (nullable — APPROVED 인증 취소 시에만 기록, 방향 B)
--   processed_by:     처리 운영자 userId (nullable — 미처리 PENDING 시 null)
--   submitted_at:     신청 시각
--   processed_at:     승인/거절 처리 시각 (nullable — 미처리 시 null)
--   created_at/updated_at: JPA @CreatedDate / @LastModifiedDate
--
-- 인덱스 2개
--   status:  목록 status 필터 조회
--   shop_id: 특정 가게의 인증 신청 조회 (취소 cascade 등)
--
-- ⚠️ shops.is_certified 컬럼은 baseline(V1)에 이미 존재 → 본 슬라이스는 shops ALTER 없음.
--    승인/취소 시 Shop.markCertified()/unmarkCertified()로 토글만 수행.
--
-- 컬럼 컨벤션은 V1 baseline / V2 admin_action_logs와 일치 (DATETIME(6), VARCHAR, utf8mb4).
-- =====================================================================

CREATE TABLE shop_certifications (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id          BIGINT       NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    submitted_reason TEXT         NULL,
    reject_reason    TEXT         NULL,
    cancel_reason    TEXT         NULL,
    processed_by     BIGINT       NULL,
    submitted_at     DATETIME(6)  NOT NULL,
    processed_at     DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_shop_cert_status (status),
    KEY idx_shop_cert_shop (shop_id),
    CONSTRAINT fk_shop_certifications_shop FOREIGN KEY (shop_id) REFERENCES shops(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
