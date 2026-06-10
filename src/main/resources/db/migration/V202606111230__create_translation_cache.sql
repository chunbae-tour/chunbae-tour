-- ============================
-- translation_cache (번역 캐시) — 정적 도메인 번역 결과 영구 저장 (content_hash + target_language UNIQUE)
-- ============================
CREATE TABLE `translation_cache` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT,
  `created_at`         datetime(6)  NOT NULL,
  `updated_at`         datetime(6)  NOT NULL,
  `content_hash`       char(64)     NOT NULL,
  `target_language`    varchar(10)  NOT NULL,
  `translated_content` text         NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_translation_cache_hash_lang` (`content_hash`, `target_language`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
