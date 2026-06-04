-- =====================================================================
-- V202606041000 — banners 테이블 신규 (KAN-216, Admin Epic KAN-177 S09)
-- =====================================================================
-- 운영자 추천 배너. priority(오름차순 정렬 키) + start_date/end_date(노출 기간)로 노출 순서·시점을 통제한다.
-- 운영자 목록(BannerRepository.searchForAdmin)은 다음 형태로 조회한다:
--   WHERE status <> 'DELETED' [AND status = ...] [AND keyset(priority, id)]
--   ORDER BY priority ASC, id DESC
-- 정렬 키(priority ASC, id DESC)가 인덱스에 없으면 status 필터 후 filesort가 발생한다.
-- (status, priority, id) 복합 인덱스로 status 범위 스캔 + 정렬을 인덱스에서 함께 처리해 filesort를 회피한다.
--
-- status는 BannerStatus enum을 @Enumerated(STRING)으로 저장 — VARCHAR(20)(ACTIVE/HIDDEN/DELETED).

CREATE TABLE `banners` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(100) NOT NULL,
  `image_url` varchar(500) NOT NULL,
  `link_url` varchar(500) DEFAULT NULL,
  `priority` int NOT NULL DEFAULT 0,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_banners_status_priority_id` (`status`, `priority`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
