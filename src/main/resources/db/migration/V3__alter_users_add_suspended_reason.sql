-- =====================================================================
-- V3 — users.suspended_reason 추가 (KAN-180, Admin Epic KAN-177 S02)
-- =====================================================================
-- 운영자 사용자 정지 사유 저장 컬럼. AdminUserService.suspend wiring용.
--
-- 주의: suspended_until 컬럼은 V1 baseline(users 테이블)에 이미 존재한다.
--       본 V3는 suspended_reason 단일 컬럼만 ADD (중복 ADD 시 마이그레이션 실패).
--
-- 컬럼 책임
--   suspended_reason: 운영자 입력 정지 사유 (nullable — 신고 자동 정지 등 사유 미입력 경로 존재)
--
-- 영향
--   nullable 단일 컬럼 추가 → 기존 row 영향 없음 (NULL 기본).
--   Account entity @Column(name="suspended_reason", length=500)과 정확히 일치 (ddl-auto: validate).
-- =====================================================================

ALTER TABLE users
    ADD COLUMN suspended_reason VARCHAR(500) NULL;
