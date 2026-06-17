-- shop_images FK에 ON DELETE 정책 명시 (coderabbit #601).
-- 기존 fk_shop_images_shop은 ON DELETE 절이 없어 InnoDB 기본값(RESTRICT)에 의존 → shops 행 hard-delete 시
--   shop_images가 남아 있으면 FK 위반으로 삭제 차단(테스트 cleanup·관리자 데이터 정리 시 문제).
-- 가게 이미지는 가게에 종속된 데이터이므로 ON DELETE CASCADE로 명시 — 가게 삭제 시 이미지 행도 함께 정리한다.
--   (운영 shop은 soft-delete(status)가 기본이라 CASCADE는 실제 row DELETE 경로에서만 동작. S3 객체 회수는 별도 cleanup 트랙.)
ALTER TABLE `shop_images` DROP FOREIGN KEY `fk_shop_images_shop`;
ALTER TABLE `shop_images`
    ADD CONSTRAINT `fk_shop_images_shop` FOREIGN KEY (`shop_id`) REFERENCES `shops` (`id`) ON DELETE CASCADE;
