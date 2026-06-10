-- target_type 컬럼의 DEFAULT 값('PLACE')을 제거 (팀 컨벤션 준수)
-- 이미 존재하는 행들은 이전 마이그레이션(1155)에서 DEFAULT 값으로 채워졌으므로 더 이상 필요하지 않음
ALTER TABLE `user_likes` ALTER COLUMN `target_type` DROP DEFAULT;
