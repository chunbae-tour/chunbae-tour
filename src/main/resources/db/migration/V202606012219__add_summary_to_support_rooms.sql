-- ============================
-- support_rooms summary 컬럼 추가 — 상담 종료 시 관리자 메모
-- ============================
ALTER TABLE `support_rooms`
    ADD COLUMN `summary` text DEFAULT NULL;
