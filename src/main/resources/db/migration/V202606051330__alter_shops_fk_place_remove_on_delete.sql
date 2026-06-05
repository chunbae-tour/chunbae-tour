-- shops.place_id FK에서 ON DELETE SET NULL 제거 (KAN-362)
-- Place는 soft delete(status=DELETED)만 사용하므로 SET NULL이 발화하지 않음
-- 연관 shop.place_id null 처리는 AdminPlaceService.deletePlace()가 직접 담당
--
-- ON DELETE 절 없이 재생성하면 MySQL default인 RESTRICT가 적용됨.
-- Place가 hard delete되면 참조 중인 shop이 있을 때 FK가 차단 — soft delete 전용 설계에서는
-- 런타임에 발화하지 않으나, 실수로 hard delete를 시도할 경우 안전하게 차단하는 의도된 동작.
--
-- [SQL이 긴 이유]
-- MySQL 8.x는 "DROP FOREIGN KEY IF EXISTS", "ADD CONSTRAINT IF NOT EXISTS" 문법을 지원하지 않음.
-- 때문에 아래 3단계 우회 패턴이 필요함 (V202606042220/V202606051304 패턴 준용):
--   1) information_schema에서 제약 존재 여부 조회
--   2) IF()로 실행할 SQL 문자열 분기 (존재하면 실제 DDL, 없으면 no-op SELECT 1)
--   3) PREPARE/EXECUTE로 문자열을 실제 SQL로 동적 실행
-- 이 패턴 없이 단순 ALTER를 쓰면 부분 실패 후 재실행 시 1025/1091 오류로 Flyway가 멈춤.

-- 1단계: FK 존재 시 DROP
-- information_schema.TABLE_CONSTRAINTS에서 FK 이름으로 존재 여부 확인
SET @fk_exists := (
    SELECT COUNT(1)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE table_schema    = DATABASE()
      AND table_name      = 'shops'
      AND constraint_name = 'fk_shops_place'
      AND constraint_type = 'FOREIGN KEY'
);

-- FK 있으면 DROP, 없으면 SELECT 1(no-op)을 동적 실행
SET @drop_fk := IF(
    @fk_exists > 0,
    'ALTER TABLE shops DROP FOREIGN KEY fk_shops_place',
    'SELECT 1'
);

PREPARE stmt FROM @drop_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2단계: FK 미존재 시 ADD (ON DELETE 절 없이 — RESTRICT)
-- DROP 후 ADD가 되어야 하지만, 부분 실패로 DROP만 된 채 재실행 시에도 안전하게 처리하기 위한 가드
SET @fk_missing := (
    SELECT COUNT(1) = 0
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE table_schema    = DATABASE()
      AND table_name      = 'shops'
      AND constraint_name = 'fk_shops_place'
      AND constraint_type = 'FOREIGN KEY'
);

-- FK 없으면 ADD, 이미 있으면 SELECT 1(no-op)을 동적 실행
SET @add_fk := IF(
    @fk_missing,
    'ALTER TABLE shops ADD CONSTRAINT fk_shops_place FOREIGN KEY (place_id) REFERENCES places (id)',
    'SELECT 1'
);

PREPARE stmt2 FROM @add_fk;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
