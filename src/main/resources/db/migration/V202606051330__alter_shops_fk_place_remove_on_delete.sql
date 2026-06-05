-- shops.place_id FK에서 ON DELETE SET NULL 제거 (KAN-362)
-- Place는 soft delete(status=DELETED)만 사용하므로 SET NULL이 발화하지 않음
-- 연관 shop.place_id null 처리는 AdminPlaceService.deletePlace()가 직접 담당
--
-- ON DELETE 절 없이 재생성하면 MySQL default인 RESTRICT가 적용됨.
-- Place가 hard delete되면 참조 중인 shop이 있을 때 FK가 차단 — soft delete 전용 설계에서는
-- 런타임에 발화하지 않으나, 실수로 hard delete를 시도할 경우 안전하게 차단하는 의도된 동작.
--
-- MySQL 8.x는 DROP FOREIGN KEY IF EXISTS 문법을 지원하지 않으므로
-- information_schema로 존재 여부를 확인 후 조건부 실행 (V202606042220/V202606051304 패턴 준용)

-- 1단계: FK 존재 시 DROP
SET @fk_exists := (
    SELECT COUNT(1)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE table_schema    = DATABASE()
      AND table_name      = 'shops'
      AND constraint_name = 'fk_shops_place'
      AND constraint_type = 'FOREIGN KEY'
);

SET @drop_fk := IF(
    @fk_exists > 0,
    'ALTER TABLE shops DROP FOREIGN KEY fk_shops_place',
    'SELECT 1'
);

PREPARE stmt FROM @drop_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2단계: FK 미존재 시 ADD (ON DELETE 절 없이 — RESTRICT)
SET @fk_missing := (
    SELECT COUNT(1) = 0
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE table_schema    = DATABASE()
      AND table_name      = 'shops'
      AND constraint_name = 'fk_shops_place'
      AND constraint_type = 'FOREIGN KEY'
);

SET @add_fk := IF(
    @fk_missing,
    'ALTER TABLE shops ADD CONSTRAINT fk_shops_place FOREIGN KEY (place_id) REFERENCES places (id)',
    'SELECT 1'
);

PREPARE stmt2 FROM @add_fk;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
