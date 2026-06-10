-- =====================================================================
-- Phase 9-1: 관광지 검색 Full-Text Index 전환
-- =====================================================================
-- name 컬럼에 FULLTEXT 인덱스를 추가하여 검색 성능 향상
-- 한글 검색의 정확도를 높이기 위해 WITH PARSER ngram 사용

ALTER TABLE `places` ADD FULLTEXT INDEX `idx_places_name_fulltext` (`name`) WITH PARSER ngram, ALGORITHM=INPLACE, LOCK=NONE;
