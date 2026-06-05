-- 1상인 다중 가게 허용 (KAN-361)
-- 코드 전반이 다중 가게를 전제로 설계되어 있으나 V1 baseline의 uk_shops_user_id가
-- user당 가게 1개를 강제 → 2번째 가게 승인 시 500 오류 발생
--
-- MySQL 8.x는 DROP INDEX IF EXISTS 문법을 지원하지 않으므로
-- information_schema로 존재 여부를 확인 후 조건부 실행 (V202606042220 패턴 준용)
SET @index_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name   = 'shops'
      AND index_name   = 'uk_shops_user_id'
);

SET @drop_sql := IF(
    @index_exists > 0,
    'ALTER TABLE shops DROP INDEX uk_shops_user_id',
    'SELECT 1'
);

PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
