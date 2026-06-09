-- Product redemption scope (KAN-251)
-- 사용처 범위. 현재 모든 스토어 상품은 GLOBAL(전 가맹점 공통). 가게/파트너/행사 한정 사용처는 추후 redemption 도메인 확장에서 도입.
ALTER TABLE products
    ADD COLUMN redemption_scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL';
