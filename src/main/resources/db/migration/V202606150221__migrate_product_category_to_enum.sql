-- KAN-302: 상품 카테고리 자유 문자열 → 고정 enum(ProductCategory) 전환.
-- products.category 컬럼(varchar 50)은 그대로 두고 저장 값만 enum 이름으로 변환(@Enumerated STRING).
-- INSERT 아님 — 기존 행의 category 문자열만 UPDATE.

-- 기존 값 매핑 (시드/운영 데이터: COUPON / TICKET / GOODS)
UPDATE products SET category = 'DISCOUNT_COUPON'  WHERE category = 'COUPON';
UPDATE products SET category = 'ADMISSION_TICKET' WHERE category = 'TICKET';
UPDATE products SET category = 'LOCAL_PRODUCT'    WHERE category = 'GOODS';

-- 미매핑 값 가드 (KAN-302 리뷰): catch-all 수렴(NOT IN → DISCOUNT_COUPON)을 제거했으므로,
-- 매핑 후에도 ProductCategory enum 5종에 없는 category가 남아 있으면 마이그레이션을 즉시 실패시켜
-- 사람이 확인하도록 강제한다 — 배포 후 JPA가 해당 row를 읽을 때 IllegalArgumentException(500)이 터지는
-- 것을 사전에 차단. 시드/운영 데이터는 COUPON/TICKET/GOODS뿐이라 정상 시 0건으로 통과한다.
DROP PROCEDURE IF EXISTS assert_product_category_mapped;
DELIMITER //
CREATE PROCEDURE assert_product_category_mapped()
BEGIN
    DECLARE unmapped INT;
    SELECT COUNT(*) INTO unmapped FROM products
     WHERE category NOT IN ('ADMISSION_TICKET', 'TOUR_PASS', 'EXPERIENCE', 'DISCOUNT_COUPON', 'LOCAL_PRODUCT');
    IF unmapped > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'KAN-302: products.category에 ProductCategory enum에 없는 미매핑 값이 존재합니다. 배포 전 수동 점검 필요.';
    END IF;
END //
DELIMITER ;
CALL assert_product_category_mapped();
DROP PROCEDURE assert_product_category_mapped;
