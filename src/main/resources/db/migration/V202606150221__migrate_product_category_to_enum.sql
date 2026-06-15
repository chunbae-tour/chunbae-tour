-- KAN-302: 상품 카테고리 자유 문자열 → 고정 enum(ProductCategory) 전환.
-- products.category 컬럼(varchar 50)은 그대로 두고 저장 값만 enum 이름으로 변환(@Enumerated STRING).
-- INSERT 아님 — 기존 행의 category 문자열만 UPDATE.

-- 기존 값 매핑 (시드/운영 데이터: COUPON / TICKET / GOODS)
UPDATE products SET category = 'DISCOUNT_COUPON'  WHERE category = 'COUPON';
UPDATE products SET category = 'ADMISSION_TICKET' WHERE category = 'TICKET';
UPDATE products SET category = 'LOCAL_PRODUCT'    WHERE category = 'GOODS';
