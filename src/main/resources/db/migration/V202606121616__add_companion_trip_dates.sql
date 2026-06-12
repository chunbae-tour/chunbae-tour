-- ============================
-- companions — 동행 여행 기간(tripStartDate/tripEndDate) 추가, 기간 겹침 검증(CR_010)에 사용
-- 기존 row가 없는 신규 도메인이라 NOT NULL DEFAULT 없이 추가
-- ============================
ALTER TABLE `companions`
  ADD COLUMN `trip_start_date` date NOT NULL AFTER `started_at`,
  ADD COLUMN `trip_end_date`   date NOT NULL AFTER `trip_start_date`;
