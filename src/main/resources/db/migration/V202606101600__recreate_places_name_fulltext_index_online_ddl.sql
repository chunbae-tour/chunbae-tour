-- =====================================================================
-- Phase 9-1: 관광지 검색 Full-Text Index Online DDL 보완
-- =====================================================================
-- 이전 마이그레이션(V202606101454)에서 생성된 FULLTEXT 인덱스를 제거하고
-- 운영 환경의 대용량 테이블 락을 방지하기 위해 ALGORITHM=INPLACE, LOCK=NONE 옵션을 포함해 재생성합니다.

ALTER TABLE `places` DROP INDEX `idx_places_name_fulltext`;
ALTER TABLE `places` ADD FULLTEXT INDEX `idx_places_name_fulltext` (`name`) WITH PARSER ngram, ALGORITHM=INPLACE, LOCK=NONE;
