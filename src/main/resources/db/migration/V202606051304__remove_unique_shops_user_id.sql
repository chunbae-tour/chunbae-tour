-- 1상인 다중 가게 허용 (KAN-361)
-- 코드 전반이 다중 가게를 전제로 설계되어 있으나 V1 baseline의 uk_shops_user_id가
-- user당 가게 1개를 강제 → 2번째 가게 승인 시 500 오류 발생
ALTER TABLE shops DROP INDEX uk_shops_user_id;
