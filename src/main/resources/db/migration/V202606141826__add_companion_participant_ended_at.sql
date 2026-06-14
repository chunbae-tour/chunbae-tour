-- ============================
-- companion_participants — 참여자 본인 동행 종료 시각(endedAt) 추가
-- 신규 컬럼이라 NULL = 아직 종료 안 함, NOT NULL 전환 불필요 (고도화 #2)
-- ============================
ALTER TABLE `companion_participants`
  ADD COLUMN `ended_at` datetime NULL AFTER `added_at`;
