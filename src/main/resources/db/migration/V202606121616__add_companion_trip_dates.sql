-- ============================
-- companions — 동행 여행 기간(tripStartDate/tripEndDate) 추가, 기간 겹침 검증(CR_010)에 사용
-- 기존 row 존재 가능성 대비 — nullable로 추가 후 started_at 날짜로 백필, 이후 NOT NULL 전환
-- (NOT NULL을 DEFAULT 없이 바로 추가하면 NO_ZERO_DATE sql_mode에서 row 존재 시 ERROR 1292)
-- ============================
ALTER TABLE `companions`
  ADD COLUMN `trip_start_date` date NULL AFTER `started_at`,
  ADD COLUMN `trip_end_date`   date NULL AFTER `trip_start_date`;

UPDATE `companions`
   SET `trip_start_date` = DATE(`started_at`),
       `trip_end_date`   = DATE(`started_at`)
 WHERE `trip_start_date` IS NULL;

ALTER TABLE `companions`
  MODIFY COLUMN `trip_start_date` date NOT NULL,
  MODIFY COLUMN `trip_end_date`   date NOT NULL;
