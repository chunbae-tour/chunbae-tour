-- =====================================================================
-- V202606031840 — places (status, id) 복합 인덱스 (KAN-209, Admin Epic KAN-177 S07)
-- =====================================================================
-- 운영자 관광지 목록(PlaceRepository.searchForAdmin)은 다음 형태로 조회한다:
--   WHERE status <> 'DELETED' [AND name LIKE ...] [AND category = ...] [AND id < :cursor]
--   ORDER BY id DESC
-- 정렬 키(id DESC)가 인덱스에 없으면 status 필터 후 filesort가 발생한다.
-- (status, id) 복합 인덱스로 status 범위 스캔 + id 정렬을 인덱스에서 함께 처리해 filesort를 회피한다.
--
-- 기존 단일 idx_places_status(status)는 본 복합의 leftmost prefix라 기능적으로 중복 → 제거한다.
-- (status 단독 조건 쿼리도 복합 인덱스가 그대로 커버.)
--
-- baseline(V1)에 idx_places_status가 존재. 엔티티 @Index도 idx_places_status_id로 동기화함.

ALTER TABLE `places` DROP INDEX `idx_places_status`;
ALTER TABLE `places` ADD INDEX `idx_places_status_id` (`status`, `id`);
